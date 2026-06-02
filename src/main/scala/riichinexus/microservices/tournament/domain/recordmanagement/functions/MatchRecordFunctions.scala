package riichinexus.microservices.tournament.domain.recordmanagement.functions

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
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.objects.paifumanagement.Paifu

object MatchRecordFunctions:
  def validate(record: MatchRecord): Unit =
    require(record.seatResults.size == 4, "Match record must contain four seat results")
    require(record.seatResults.map(_.playerId).distinct.size == 4, "Match record players must be unique")
    require(record.seatResults.map(_.seat).distinct.size == 4, "Match record seats must be unique")
    require(record.seatResults.map(_.placement).distinct.size == 4, "Match record placements must be unique")
    require(record.stageRoundNumber >= 1, "Match record stage round number must be positive")

  def playerIds(record: MatchRecord): Vector[PlayerId] =
    record.seatResults.map(_.playerId)

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

    MatchRecord(
      id = TournamentIdGenerator.matchRecordId(),
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
      notes = Vector.empty
    )
