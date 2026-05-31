package riichinexus.microservices.tournament.domain.model

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.AppealTableResolution
import riichinexus.microservices.tournament.objects.{SeatWind, TableStatus}

final case class Table(
    id: TableId,
    tableNo: Int,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    stageRoundNumber: Int = 1,
    bracketMatchId: Option[String] = None,
    bracketRoundNumber: Option[Int] = None,
    feederMatchIds: Vector[String] = Vector.empty,
    status: TableStatus = TableStatus.WaitingPreparation,
    startedAt: Option[Instant] = None,
    scoringStartedAt: Option[Instant] = None,
    endedAt: Option[Instant] = None,
    paifuId: Option[PaifuId] = None,
    matchRecordId: Option[MatchRecordId] = None,
    appealTicketIds: Vector[AppealTicketId] = Vector.empty,
    resetCount: Int = 0,
    operatorNotes: Vector[String] = Vector.empty,
    version: Int = 0
) derives CanEqual:
  require(seats.size == 4, "A riichi table must have exactly four seats")
  require(seats.map(_.seat).distinct.size == 4, "Seats must be unique")
  require(stageRoundNumber >= 1, "Stage round number must be positive")

  def seatFor(wind: SeatWind): TableSeat =
    seats.find(_.seat == wind).getOrElse(
      throw NoSuchElementException(s"Seat $wind was not found on table ${id.value}")
    )

  def allSeatsReady: Boolean =
    seats.forall(_.ready)

  def hasDisconnectedSeats: Boolean =
    seats.exists(_.disconnected)

  def updateSeatState(
      targetSeat: SeatWind,
      ready: Option[Boolean] = None,
      disconnected: Option[Boolean] = None,
      note: Option[String] = None
  ): Table =
    require(status != TableStatus.Archived, "Archived tables cannot update seat state")
    if ready.isDefined then
      require(
        status == TableStatus.WaitingPreparation,
        "Seat readiness can only be updated before a table starts"
      )

    val updatedSeats = seats.map { seat =>
      if seat.seat != targetSeat then seat
      else
        val withConnection = disconnected match
          case Some(true)  => seat.markDisconnected
          case Some(false) => seat.markConnected
          case None        => seat

        ready match
          case Some(true)  => withConnection.markReady
          case Some(false) => withConnection.markNotReady
          case None        => withConnection
    }

    copy(
      seats = updatedSeats,
      operatorNotes = operatorNotes ++ note.toVector
    )

  def bindKnockoutMatch(
      matchId: String,
      roundNumber: Int,
      feeders: Vector[String] = Vector.empty
  ): Table =
    copy(
      bracketMatchId = Some(matchId),
      bracketRoundNumber = Some(roundNumber),
      feederMatchIds = feeders.distinct
    )

  def start(at: Instant): Table =
    require(
      status == TableStatus.WaitingPreparation,
      "Only waiting tables can be started"
    )
    val preparedSeats =
      if seats.forall(seat => !seat.ready && !seat.disconnected) then seats.map(_.markReady)
      else seats

    require(preparedSeats.forall(_.ready), "All seats must be ready before a table starts")
    require(!preparedSeats.exists(_.disconnected), "Disconnected seats must reconnect before a table starts")
    copy(status = TableStatus.InProgress, seats = preparedSeats, startedAt = Some(at))

  def enterScoring(at: Instant): Table =
    require(status == TableStatus.InProgress, "Only running tables can enter scoring")
    copy(status = TableStatus.Scoring, scoringStartedAt = Some(at))

  def archive(
      recordId: MatchRecordId,
      paifuId: PaifuId,
      at: Instant,
      note: Option[String] = None
  ): Table =
    require(status == TableStatus.Scoring, "Only scoring tables can be archived")
    copy(
      status = TableStatus.Archived,
      scoringStartedAt = Some(scoringStartedAt.getOrElse(at)),
      endedAt = Some(at),
      paifuId = Some(paifuId),
      matchRecordId = Some(recordId),
      operatorNotes = operatorNotes ++ note.toVector
    )

  def flagAppeal(ticketId: AppealTicketId, note: Option[String] = None): Table =
    require(status != TableStatus.Archived, "Archived tables cannot enter appeal flow")
    copy(
      status = TableStatus.AppealInProgress,
      appealTicketIds = (appealTicketIds :+ ticketId).distinct,
      operatorNotes = operatorNotes ++ note.toVector
    )

  def resolveAppeal(
      resolution: AppealTableResolution = AppealTableResolution.RestorePriorState,
      note: Option[String] = None
  ): Table =
    require(status == TableStatus.AppealInProgress, "Only appealed tables can resolve appeals")
    resolution match
      case AppealTableResolution.ForceReset =>
        forceReset(note.getOrElse("appeal adjudication requested a table reset"), Instant.now())
      case _ =>
        copy(
          status =
            resolution match
              case AppealTableResolution.RestorePriorState =>
                if endedAt.nonEmpty || matchRecordId.nonEmpty || paifuId.nonEmpty then TableStatus.Archived
                else if scoringStartedAt.nonEmpty then TableStatus.Scoring
                else if startedAt.nonEmpty then TableStatus.InProgress
                else TableStatus.WaitingPreparation
              case AppealTableResolution.ArchiveTable => TableStatus.Archived
              case AppealTableResolution.ResumeScoring => TableStatus.Scoring
              case AppealTableResolution.ResumePlay    => TableStatus.InProgress
              case AppealTableResolution.ForceReset    => TableStatus.WaitingPreparation
          ,
          operatorNotes = operatorNotes ++ note.toVector
        )

  def forceReset(note: String, at: Instant): Table =
    copy(
      status = TableStatus.WaitingPreparation,
      seats = seats.map(_.copy(disconnected = false, ready = false)),
      startedAt = None,
      scoringStartedAt = None,
      endedAt = None,
      paifuId = None,
      matchRecordId = None,
      resetCount = resetCount + 1,
      operatorNotes = operatorNotes :+ s"${at.toString}: $note"
    )

