package riichinexus.domain.model

import java.time.Instant
import java.util.NoSuchElementException

final case class TableSeat(
    seat: SeatWind,
    playerId: PlayerId,
    initialPoints: Int = 25000,
    disconnected: Boolean = false,
    ready: Boolean = false,
    clubId: Option[ClubId] = None
) derives CanEqual:
  require(initialPoints > 0, "Seat initial points must be positive")

  def markReady: TableSeat =
    require(!disconnected, "Disconnected seats cannot be marked ready")
    copy(ready = true)

  def markNotReady: TableSeat =
    copy(ready = false)

  def markDisconnected: TableSeat =
    copy(disconnected = true, ready = false)

  def markConnected: TableSeat =
    copy(disconnected = false)

enum TableStatus derives CanEqual:
  case WaitingPreparation
  case InProgress
  case Scoring
  case Archived
  case AppealInProgress

object TableStatus:
  val Pending: TableStatus = WaitingPreparation
  val Finished: TableStatus = Archived

enum AppealTableResolution derives CanEqual:
  case RestorePriorState
  case ArchiveTable
  case ResumeScoring
  case ResumePlay
  case ForceReset

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

final case class MatchRecordSeatResult(
    playerId: PlayerId,
    seat: SeatWind,
    clubId: Option[ClubId] = None,
    finalPoints: Int,
    placement: Int,
    scoreDelta: Int,
    uma: Double = 0.0,
    oka: Double = 0.0
) derives CanEqual:
  require(placement >= 1 && placement <= 4, "Placement must be between 1 and 4")

final case class MatchRecord(
    id: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    stageRoundNumber: Int,
    generatedAt: Instant,
    seatResults: Vector[MatchRecordSeatResult],
    paifuId: Option[PaifuId] = None,
    finalizedBy: Option[PlayerId] = None,
    sourceEvent: String = "table-state-machine",
    notes: Vector[String] = Vector.empty
) derives CanEqual:
  require(seatResults.size == 4, "Match record must contain four seat results")
  require(seatResults.map(_.playerId).distinct.size == 4, "Match record players must be unique")
  require(seatResults.map(_.seat).distinct.size == 4, "Match record seats must be unique")
  require(seatResults.map(_.placement).distinct.size == 4, "Match record placements must be unique")
  require(stageRoundNumber >= 1, "Match record stage round number must be positive")

  def playerIds: Vector[PlayerId] =
    seatResults.map(_.playerId)

object MatchRecord:
  def fromTableAndPaifu(
      table: Table,
      paifu: Paifu,
      generatedAt: Instant,
      finalizedBy: Option[PlayerId] = None
  ): MatchRecord =
    val seatMap = table.seats.map(seat => seat.playerId -> seat).toMap
    require(
      paifu.finalStandings.map(_.playerId).toSet == seatMap.keySet,
      "Paifu final standings must match scheduled table players"
    )

    val settlementNotes = paifu.rounds.zipWithIndex.flatMap { (round, index) =>
      round.result.settlement.map { settlement =>
        val noteSuffix =
          if settlement.notes.isEmpty then ""
          else s" notes=${settlement.notes.mkString("|")}"
        s"round-${index + 1}:${round.descriptor.roundWind}-${round.descriptor.handNumber} settlement riichi=${settlement.riichiSticksDelta} honba=${settlement.honbaPayment}$noteSuffix"
      }
    }

    MatchRecord(
      id = IdGenerator.matchRecordId(),
      tableId = table.id,
      tournamentId = table.tournamentId,
      stageId = table.stageId,
      stageRoundNumber = table.stageRoundNumber,
      generatedAt = generatedAt,
      seatResults = paifu.finalStandings.map { standing =>
        val scheduledSeat = seatMap(standing.playerId)
        MatchRecordSeatResult(
          playerId = standing.playerId,
          seat = standing.seat,
          clubId = scheduledSeat.clubId,
          finalPoints = standing.finalPoints,
          placement = standing.placement,
          scoreDelta = standing.finalPoints - scheduledSeat.initialPoints,
          uma = standing.uma,
          oka = standing.oka
        )
      },
      paifuId = Some(paifu.id),
      finalizedBy = finalizedBy,
      notes = settlementNotes
    )
