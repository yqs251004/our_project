package riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking

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
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.{StageRankingSnapshot, StageStandingEntry}
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat

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

private[tournament] object TournamentStageRankingBuilder:
  def build(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerId],
      records: Vector[MatchRecord],
      at: Instant
  ): StageRankingSnapshot =
    val effectiveRecords = rankingRecords(stage, records)
    val placementPointMap = Map(1 -> 4, 2 -> 2, 3 -> 1, 4 -> 0)

    val statsByPlayer = effectiveRecords
      .flatMap(_.seatResults)
      .groupBy(_.playerId)
      .view
      .mapValues { results =>
        val matchesPlayed = results.size
        val placementPoints = results.map(result => placementPointMap.getOrElse(result.placement, 0)).sum
        val totalScoreDelta = results.map(_.scoreDelta).sum
        val totalFinalPoints = results.map(_.finalPoints).sum
        val averagePlacement =
          if matchesPlayed <= 0 then 99.0
          else round2(results.map(_.placement.toDouble).sum / matchesPlayed.toDouble)

        StageStandingEntry(
          playerId = results.head.playerId,
          matchesPlayed = matchesPlayed,
          placementPoints = placementPoints,
          totalScoreDelta = totalScoreDelta,
          totalFinalPoints = totalFinalPoints,
          averagePlacement = averagePlacement
        )
      }
      .toMap

    val seededParticipants =
      participants
        .distinct
        .zipWithIndex
        .map { case (playerId, index) =>
          statsByPlayer.getOrElse(
            playerId,
            StageStandingEntry(
              playerId = playerId,
              matchesPlayed = 0,
              placementPoints = 0,
              totalScoreDelta = 0,
              totalFinalPoints = 0,
              averagePlacement = 99.0,
              seed = Some(index + 1)
            )
          )
        }

    val entries = (statsByPlayer.values.toVector ++ seededParticipants)
      .groupBy(_.playerId)
      .values
      .map(_.maxBy(entry => (entry.matchesPlayed, entry.placementPoints, entry.totalScoreDelta)))
      .toVector
      .sortBy(entry =>
        (
          -entry.placementPoints,
          -entry.totalScoreDelta,
          -entry.totalFinalPoints,
          entry.averagePlacement,
          entry.playerId.value
        )
      )
      .zipWithIndex
      .map { case (entry, index) => entry.copy(seed = Some(index + 1)) }

    StageRankingSnapshot(
      tournamentId = tournament.id,
      stageId = stage.id,
      generatedAt = at,
      entries = entries,
      archivedTableCount = effectiveRecords.map(_.tableId).distinct.size,
      scheduledTableCount = stage.scheduledTableIds.size
    )

  private def rankingRecords(
      stage: TournamentStage,
      records: Vector[MatchRecord]
  ): Vector[MatchRecord] =
    if stage.format != TournamentFormat.Swiss || stage.swissRule.forall(_.carryOverPoints) then records
    else
      val latestRound = records.map(_.stageRoundNumber).foldLeft(0)(math.max)
      if latestRound <= 0 then records
      else records.filter(_.stageRoundNumber == latestRound)

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
