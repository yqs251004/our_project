package riichinexus.microservices.tournament.domain.settlementmanagement.functions

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
import riichinexus.microservices.tournament.objects.settlementmanagement.{TournamentSettlementAdjustment, TournamentSettlementEntry, TournamentSettlementStatus}

import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutLane
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.{StageRankingSnapshot, StageStandingEntry}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentFormat, TournamentStatus}

import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.application.ports.{AuditEventRepository, DomainEventBus, TransactionManager}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.AuthorizationPolicy
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.TournamentSettlementSnapshotFunctions
import riichinexus.microservices.tournament.domain.events.TournamentSettlementRecorded
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*

final class TournamentSettlementCoordinator(
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationPolicy
):
  def settleTournament(
      connection: Connection,
      tournamentId: TournamentId,
      finalStageId: TournamentStageId,
      actor: AccessPrincipal,
      settledAt: Instant,
      prizePool: Long,
      payoutRatios: Vector[Double],
      houseFeeAmount: Long,
      clubShareRatio: Double,
      adjustments: Vector[TournamentSettlementAdjustment],
      finalizeSettlement: Boolean,
      note: Option[String]
  ): TournamentSettlementSnapshot =
    val settlement = SettlementInput(
      tournamentId = tournamentId,
      finalStageId = finalStageId,
      actor = actor,
      settledAt = settledAt,
      prizePool = prizePool,
      payoutRatios = payoutRatios,
      houseFeeAmount = houseFeeAmount,
      clubShareRatio = clubShareRatio,
      adjustments = adjustments,
      finalizeSettlement = finalizeSettlement,
      note = note
    )
    validateSettlementInput(settlement)

    val tournament = requireTournament(connection, settlement.tournamentId)
    val finalStage = requireStage(tournament, settlement.finalStageId)
    authorizationService.requirePermission(
      settlement.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(settlement.tournamentId)
    )

    val ranking = TournamentStageQueries.stageStandings(
      connection,
      settlement.tournamentId,
      settlement.finalStageId,
      settlement.settledAt
    )
    val resolvedPlayers =
      resolveSettlementPlayers(connection, settlement, finalStage, ranking)
    val previousSnapshot =
      riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable.findByTournamentAndStage(connection, settlement.tournamentId, settlement.finalStageId)

    supersedePreviousSnapshot(connection, previousSnapshot, settlement.settledAt)
    completeTournamentIfReady(connection, tournament)

    val snapshot = buildSettlementSnapshot(
      connection = connection,
      settlement = settlement,
      ranking = ranking,
      resolvedPlayers = resolvedPlayers,
      previousSnapshot = previousSnapshot
    )
    commitSettlement(connection, settlement, snapshot)

  private def validateSettlementInput(settlement: SettlementInput): Unit =
    require(settlement.prizePool >= 0L, "Prize pool must be non-negative")
    require(settlement.houseFeeAmount >= 0L, "House fee amount must be non-negative")
    require(settlement.houseFeeAmount <= settlement.prizePool, "House fee amount cannot exceed prize pool")
    require(
      settlement.clubShareRatio >= 0.0 && settlement.clubShareRatio <= 1.0,
      "Club share ratio must be between 0.0 and 1.0"
    )

  private def requireTournament(
      connection: Connection,
      tournamentId: TournamentId
  ): Tournament =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable
      .findById(connection, tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def resolveSettlementPlayers(
      connection: Connection,
      settlement: SettlementInput,
      finalStage: TournamentStage,
      ranking: StageRankingSnapshot
  ): Vector[PlayerId] =
    if isKnockoutStage(finalStage) then
      resolveKnockoutSettlementPlayers(connection, settlement, finalStage, ranking)
    else ranking.entries.map(_.playerId)

  private def resolveKnockoutSettlementPlayers(
      connection: Connection,
      settlement: SettlementInput,
      finalStage: TournamentStage,
      ranking: StageRankingSnapshot
  ): Vector[PlayerId] =
    val bracket =
      TournamentStageQueries.stageKnockoutBracket(connection, settlement.tournamentId, settlement.finalStageId, settlement.settledAt)
    val championshipFinal = bracket.rounds
      .flatMap(_.matches)
      .find(matchNode => matchNode.lane == KnockoutLane.Championship && matchNode.nextMatchId.isEmpty)
      .getOrElse {
        throw IllegalArgumentException(s"Stage ${settlement.finalStageId.value} does not contain a championship final")
      }
    if !championshipFinal.completed then
      throw IllegalArgumentException(
        s"Final knockout match ${championshipFinal.id} must be completed before settlement"
      )

    val bronzeMatch = bracket.rounds
      .flatMap(_.matches)
      .find(_.lane == KnockoutLane.Bronze)
    val repechageFinal = bracket.rounds
      .flatMap(_.matches)
      .filter(_.lane == KnockoutLane.Repechage)
      .find(_.nextMatchId.isEmpty)

    if finalStage.knockoutRule.exists(_.thirdPlaceMatch) && bronzeMatch.exists(!_.completed) then
      throw IllegalArgumentException(
        s"Bronze match must be completed before settlement for stage ${settlement.finalStageId.value}"
      )
    if finalStage.knockoutRule.exists(_.repechageEnabled) && repechageFinal.exists(!_.completed) then
      throw IllegalArgumentException(
        s"Repechage final must be completed before settlement for stage ${settlement.finalStageId.value}"
      )

    val championshipPlayers = championshipFinal.results.sortBy(_.placement).map(_.playerId)
    val bronzePlayers = bronzeMatch.toVector.flatMap { matchNode =>
      if !matchNode.completed then Vector.empty
      else matchNode.results.sortBy(_.placement).map(_.playerId)
    }
    val repechagePlayers = repechageFinal.toVector.flatMap { matchNode =>
      if !matchNode.completed then Vector.empty
      else matchNode.results.sortBy(_.placement).map(_.playerId)
    }
    val bracketPlayers = championshipPlayers ++ bronzePlayers ++ repechagePlayers

    bracketPlayers ++ ranking.entries.map(_.playerId).filterNot(bracketPlayers.contains)

  private def supersedePreviousSnapshot(
      connection: Connection,
      previousSnapshot: Option[TournamentSettlementSnapshot],
      settledAt: Instant
  ): Unit =
    previousSnapshot
      .filter(_.status != TournamentSettlementStatus.Superseded)
      .foreach(existing => riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable.save(connection, TournamentSettlementSnapshotFunctions.supersede(existing, settledAt)))

  private def completeTournamentIfReady(connection: Connection, tournament: Tournament): Unit =
    if tournament.stages.forall(_.status == StageStatus.Completed) && tournament.status != TournamentStatus.Completed then
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, TournamentFunctions.complete(tournament))

  private def buildSettlementSnapshot(
      connection: Connection,
      settlement: SettlementInput,
      ranking: StageRankingSnapshot,
      resolvedPlayers: Vector[PlayerId],
      previousSnapshot: Option[TournamentSettlementSnapshot]
  ): TournamentSettlementSnapshot =
    val effectivePayoutRatios =
      if settlement.payoutRatios.nonEmpty then settlement.payoutRatios
      else TournamentRuntimeDefaults.settlementPayoutRatios
    val netPrizePool = settlement.prizePool - settlement.houseFeeAmount
    val baseAwards = allocatePrizePool(netPrizePool, effectivePayoutRatios, resolvedPlayers.size)
    val rankingByPlayer = ranking.entries.map(entry => entry.playerId -> entry).toMap
    val adjustmentsByPlayer = settlement.adjustments.groupBy(_.playerId)
    val championId = resolvedPlayers.headOption.getOrElse {
      throw IllegalArgumentException(s"Stage ${settlement.finalStageId.value} does not contain any ranked players")
    }
    val revision = previousSnapshot.map(_.revision + 1).getOrElse(1)

    TournamentSettlementSnapshot(
      id = IdGenerator.settlementSnapshotId(),
      tournamentId = settlement.tournamentId,
      stageId = settlement.finalStageId,
      revision = revision,
      status =
        if settlement.finalizeSettlement then TournamentSettlementStatus.Finalized
        else TournamentSettlementStatus.Draft,
      generatedAt = settlement.settledAt,
      finalizedAt = if settlement.finalizeSettlement then Some(settlement.settledAt) else None,
      supersedesSettlementId = previousSnapshot.map(_.id),
      championId = championId,
      prizePool = settlement.prizePool,
      houseFeeAmount = settlement.houseFeeAmount,
      netPrizePool = netPrizePool,
      clubShareRatio = settlement.clubShareRatio,
      adjustments = settlement.adjustments,
      entries = buildSettlementEntries(
        connection = connection,
        settlement = settlement,
        resolvedPlayers = resolvedPlayers,
        baseAwards = baseAwards,
        rankingByPlayer = rankingByPlayer,
        adjustmentsByPlayer = adjustmentsByPlayer
      ),
      summary =
        s"Champion ${championId.value} settled from stage ${settlement.finalStageId.value} " +
          s"(revision $revision, status ${if settlement.finalizeSettlement then "finalized" else "draft"}) " +
          s"with gross pool ${settlement.prizePool} and net pool $netPrizePool."
    )

  private def buildSettlementEntries(
      connection: Connection,
      settlement: SettlementInput,
      resolvedPlayers: Vector[PlayerId],
      baseAwards: Vector[Long],
      rankingByPlayer: Map[PlayerId, StageStandingEntry],
      adjustmentsByPlayer: Map[PlayerId, Vector[TournamentSettlementAdjustment]]
  ): Vector[TournamentSettlementEntry] =
    resolvedPlayers.zipWithIndex.map { case (playerId, index) =>
      val standing = rankingByPlayer.getOrElse(
        playerId,
        StageStandingEntry(playerId, 0, 0, 0, 0, 99.0)
      )
      val adjustmentAmount =
        adjustmentsByPlayer.getOrElse(playerId, Vector.empty).filter(_.amount > 0L).map(_.amount).sum
      val deductionAmount =
        adjustmentsByPlayer.getOrElse(playerId, Vector.empty).filter(_.amount < 0L).map(adjustment => math.abs(adjustment.amount)).sum
      val netAwardAmount = baseAwards.lift(index).getOrElse(0L) + adjustmentAmount - deductionAmount
      val clubId = GetPlayerAPIMessage.findPlayer(connection, playerId)
        .flatMap(_.boundClubIds.headOption)
      val clubShareAmount =
        if clubId.nonEmpty then math.floor(netAwardAmount.toDouble * settlement.clubShareRatio).toLong
        else 0L
      TournamentSettlementEntry(
        playerId = playerId,
        rank = index + 1,
        awardAmount = netAwardAmount,
        baseAwardAmount = baseAwards.lift(index).getOrElse(0L),
        adjustmentAmount = adjustmentAmount,
        deductionAmount = deductionAmount,
        clubId = clubId,
        clubShareAmount = math.max(0L, clubShareAmount),
        playerRetainedAmount = netAwardAmount - math.max(0L, clubShareAmount),
        finalPoints = standing.totalFinalPoints,
        champion = index == 0
      )
    }

  private def commitSettlement(
      connection: Connection,
      settlement: SettlementInput,
      snapshot: TournamentSettlementSnapshot
  ): TournamentSettlementSnapshot =
    DomainChangeInterpreter
      .auditAndEvents(transactionManager, auditEventRepository, eventBus)
      .commitWithinTransaction(
        DomainChange(
          aggregate = snapshot,
          persist = savedSnapshot => riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable.save(connection, savedSnapshot),
          auditEntries = savedSnapshot =>
            Vector(
              AuditEventEntry(
                id = IdGenerator.auditEventId(),
                aggregateType = "tournament",
                aggregateId = settlement.tournamentId.value,
                eventType = "TournamentSettlementRecorded",
                occurredAt = settlement.settledAt,
                actorId = settlement.actor.playerId,
                details = Map(
                  "stageId" -> settlement.finalStageId.value,
                  "championId" -> savedSnapshot.championId.value,
                  "prizePool" -> settlement.prizePool.toString,
                  "netPrizePool" -> savedSnapshot.netPrizePool.toString,
                  "houseFeeAmount" -> settlement.houseFeeAmount.toString,
                  "clubShareRatio" -> settlement.clubShareRatio.toString,
                  "revision" -> savedSnapshot.revision.toString,
                  "status" -> savedSnapshot.status.toString
                ),
                note = settlement.note.orElse(Some(savedSnapshot.summary))
              )
            ),
          domainEvents = savedSnapshot =>
            Vector(TournamentSettlementRecorded(savedSnapshot, settlement.settledAt))
        )
      )

  private def isKnockoutStage(stage: TournamentStage): Boolean =
    stage.format == TournamentFormat.Knockout ||
      stage.format == TournamentFormat.Finals ||
      stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

  private def allocatePrizePool(
      prizePool: Long,
      payoutRatios: Vector[Double],
      participantCount: Int
  ): Vector[Long] =
    if prizePool <= 0L || participantCount <= 0 then Vector.fill(participantCount)(0L)
    else
      val normalizedRatios =
        if payoutRatios.isEmpty then Vector(1.0)
        else payoutRatios.map(ratio => math.max(0.0, ratio))

      val ratioSum = normalizedRatios.sum
      val effectiveRatios =
        if ratioSum <= 0.0 then Vector(1.0)
        else normalizedRatios.map(_ / ratioSum)

      val paidSlots = math.min(participantCount, effectiveRatios.size)
      val baseAwards = effectiveRatios.take(paidSlots).map(ratio => math.floor(prizePool.toDouble * ratio).toLong)
      val remainder = prizePool - baseAwards.sum
      val adjustedAwards =
        if baseAwards.isEmpty then Vector.empty
        else baseAwards.updated(0, baseAwards.head + remainder)

      adjustedAwards ++ Vector.fill(participantCount - paidSlots)(0L)

  private final case class SettlementInput(
      tournamentId: TournamentId,
      finalStageId: TournamentStageId,
      actor: AccessPrincipal,
      settledAt: Instant,
      prizePool: Long,
      payoutRatios: Vector[Double],
      houseFeeAmount: Long,
      clubShareRatio: Double,
      adjustments: Vector[TournamentSettlementAdjustment],
      finalizeSettlement: Boolean,
      note: Option[String]
  )
