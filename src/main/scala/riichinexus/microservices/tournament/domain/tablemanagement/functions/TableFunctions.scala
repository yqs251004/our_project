package riichinexus.microservices.tournament.domain.tablemanagement.functions

import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.tournament.appeal.domain.model.AppealTableResolution
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableSeat, TableStatus}

object TableFunctions:
  def validate(table: Table): Table =
    require(table.seats.size == 4, "A riichi table must have exactly four seats")
    require(table.seats.map(_.seat).distinct.size == 4, "Seats must be unique")
    require(table.stageRoundNumber >= 1, "Stage round number must be positive")
    table.seats.foreach(TableSeatFunctions.validate)
    table

  def seatFor(table: Table, wind: SeatWind): TableSeat =
    validate(table)
    table.seats.find(_.seat == wind).getOrElse(
      throw NoSuchElementException(s"Seat $wind was not found on table ${table.id.value}")
    )

  def allSeatsReady(table: Table): Boolean =
    validate(table)
    table.seats.forall(_.ready)

  def hasDisconnectedSeats(table: Table): Boolean =
    validate(table)
    table.seats.exists(_.disconnected)

  def updateSeatState(
      table: Table,
      targetSeat: SeatWind,
      ready: Option[Boolean] = None,
      disconnected: Option[Boolean] = None,
      note: Option[String] = None
  ): Table =
    validate(table)
    require(table.status != TableStatus.Archived, "Archived tables cannot update seat state")
    if ready.isDefined then
      require(
        table.status == TableStatus.WaitingPreparation,
        "Seat readiness can only be updated before a table starts"
      )

    val updatedSeats = table.seats.map { seat =>
      if seat.seat != targetSeat then seat
      else
        val withConnection = disconnected match
          case Some(true)  => TableSeatFunctions.markDisconnected(seat)
          case Some(false) => TableSeatFunctions.markConnected(seat)
          case None        => seat

        ready match
          case Some(true)  => TableSeatFunctions.markReady(withConnection)
          case Some(false) => TableSeatFunctions.markNotReady(withConnection)
          case None        => withConnection
    }

    table.copy(
      seats = updatedSeats,
      operatorNotes = table.operatorNotes ++ note.toVector
    )

  def bindKnockoutMatch(
      table: Table,
      matchId: String,
      roundNumber: Int,
      feeders: Vector[String] = Vector.empty
  ): Table =
    validate(table)
    table.copy(
      bracketMatchId = Some(matchId),
      bracketRoundNumber = Some(roundNumber),
      feederMatchIds = feeders.distinct
    )

  def start(table: Table, at: Instant): Table =
    validate(table)
    require(
      table.status == TableStatus.WaitingPreparation,
      "Only waiting tables can be started"
    )
    val preparedSeats =
      if table.seats.forall(seat => !seat.ready && !seat.disconnected) then table.seats.map(TableSeatFunctions.markReady)
      else table.seats

    require(preparedSeats.forall(_.ready), "All seats must be ready before a table starts")
    require(!preparedSeats.exists(_.disconnected), "Disconnected seats must reconnect before a table starts")
    table.copy(status = TableStatus.InProgress, seats = preparedSeats, startedAt = Some(at))

  def enterScoring(table: Table, at: Instant): Table =
    validate(table)
    require(table.status == TableStatus.InProgress, "Only running tables can enter scoring")
    table.copy(status = TableStatus.Scoring, scoringStartedAt = Some(at))

  def archive(
      table: Table,
      recordId: MatchRecordId,
      paifuId: PaifuId,
      at: Instant,
      note: Option[String] = None
  ): Table =
    validate(table)
    require(table.status == TableStatus.Scoring, "Only scoring tables can be archived")
    table.copy(
      status = TableStatus.Archived,
      scoringStartedAt = Some(table.scoringStartedAt.getOrElse(at)),
      endedAt = Some(at),
      paifuId = Some(paifuId),
      matchRecordId = Some(recordId),
      operatorNotes = table.operatorNotes ++ note.toVector
    )

  def recordScoringResult(
      table: Table,
      recordId: MatchRecordId,
      paifuId: PaifuId,
      at: Instant,
      note: Option[String] = None
  ): Table =
    validate(table)
    val scoringTable =
      table.status match
        case TableStatus.InProgress =>
          enterScoring(table, at)
        case TableStatus.Scoring =>
          table
        case _ =>
          throw IllegalArgumentException("Only running or scoring tables can record scoring results")

    scoringTable.copy(
      scoringStartedAt = Some(scoringTable.scoringStartedAt.getOrElse(at)),
      paifuId = Some(paifuId),
      matchRecordId = Some(recordId),
      operatorNotes = scoringTable.operatorNotes ++ note.toVector
    )

  def flagAppeal(table: Table, ticketId: AppealTicketId, note: Option[String] = None): Table =
    validate(table)
    require(table.status == TableStatus.Scoring, "Only scoring tables can enter appeal flow")
    table.copy(
      status = TableStatus.AppealInProgress,
      appealTicketIds = (table.appealTicketIds :+ ticketId).distinct,
      operatorNotes = table.operatorNotes ++ note.toVector
    )

  def resolveAppeal(
      table: Table,
      resolution: AppealTableResolution = AppealTableResolution.RestorePriorState,
      note: Option[String] = None
  ): Table =
    validate(table)
    require(table.status == TableStatus.AppealInProgress, "Only appealed tables can resolve appeals")
    resolution match
      case AppealTableResolution.ForceReset =>
        forceReset(table, note.getOrElse("appeal adjudication requested a table reset"), Instant.now())
      case _ =>
        table.copy(
          status =
            resolution match
              case AppealTableResolution.RestorePriorState =>
                if table.endedAt.nonEmpty then TableStatus.Archived
                else if table.scoringStartedAt.nonEmpty || table.matchRecordId.nonEmpty || table.paifuId.nonEmpty then TableStatus.Scoring
                else if table.startedAt.nonEmpty then TableStatus.InProgress
                else TableStatus.WaitingPreparation
              case AppealTableResolution.ArchiveTable => TableStatus.Archived
              case AppealTableResolution.ResumeScoring => TableStatus.Scoring
              case AppealTableResolution.ResumePlay    => TableStatus.InProgress
              case AppealTableResolution.ForceReset    => TableStatus.WaitingPreparation
          ,
          operatorNotes = table.operatorNotes ++ note.toVector
        )

  def forceReset(table: Table, note: String, at: Instant): Table =
    validate(table)
    table.copy(
      status = TableStatus.WaitingPreparation,
      seats = table.seats.map(_.copy(disconnected = false, ready = false)),
      startedAt = None,
      scoringStartedAt = None,
      endedAt = None,
      paifuId = None,
      matchRecordId = None,
      resetCount = table.resetCount + 1,
      operatorNotes = table.operatorNotes :+ s"${at.toString}: $note"
    )
