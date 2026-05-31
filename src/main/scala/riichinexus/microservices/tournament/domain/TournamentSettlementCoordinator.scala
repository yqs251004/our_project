package riichinexus.microservices.tournament.domain

import riichinexus.microservices.tournament.objects.{TournamentSettlementStatus}

import riichinexus.microservices.tournament.objects.{AdvancementRuleType, KnockoutLane, StageStatus, TournamentFormat, TournamentStatus}

import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.application.ports.{AuditEventRepository, DomainEventBus, TransactionManager}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.AuthorizationPolicy
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.domain.model.*

final class TournamentSettlementCoordinator(
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationPolicy
):
  def settleTournament(
      connection: Connection,
      command: SettleTournamentCommand
  ): TournamentSettlementSnapshot =
    validateSettlementCommand(command)

    val tournament = requireTournament(connection, command.tournamentId)
    val finalStage = requireStage(tournament, command.finalStageId)
    authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )

    val ranking = TournamentStageQueries.stageStandings(
      connection,
      command.tournamentId,
      command.finalStageId,
      command.settledAt
    )
    val resolvedPlayers =
      resolveSettlementPlayers(connection, command, finalStage, ranking)
    val previousSnapshot =
      riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable.findByTournamentAndStage(connection, command.tournamentId, command.finalStageId)

    supersedePreviousSnapshot(connection, previousSnapshot, command.settledAt)
    completeTournamentIfReady(connection, tournament)

    val snapshot = buildSettlementSnapshot(
      connection = connection,
      command = command,
      ranking = ranking,
      resolvedPlayers = resolvedPlayers,
      previousSnapshot = previousSnapshot
    )
    commitSettlement(connection, command, snapshot)

  private def validateSettlementCommand(command: SettleTournamentCommand): Unit =
    require(command.prizePool >= 0L, "Prize pool must be non-negative")
    require(command.houseFeeAmount >= 0L, "House fee amount must be non-negative")
    require(command.houseFeeAmount <= command.prizePool, "House fee amount cannot exceed prize pool")
    require(
      command.clubShareRatio >= 0.0 && command.clubShareRatio <= 1.0,
      "Club share ratio must be between 0.0 and 1.0"
    )

  private def requireTournament(
      connection: Connection,
      tournamentId: TournamentId
  ): Tournament =
    riichinexus.microservices.tournament.tables.tournament.TournamentTable
      .findById(connection, tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def resolveSettlementPlayers(
      connection: Connection,
      command: SettleTournamentCommand,
      finalStage: TournamentStage,
      ranking: StageRankingSnapshot
  ): Vector[PlayerId] =
    if isKnockoutStage(finalStage) then
      resolveKnockoutSettlementPlayers(connection, command, finalStage, ranking)
    else ranking.entries.map(_.playerId)

  private def resolveKnockoutSettlementPlayers(
      connection: Connection,
      command: SettleTournamentCommand,
      finalStage: TournamentStage,
      ranking: StageRankingSnapshot
  ): Vector[PlayerId] =
    val bracket =
      TournamentStageQueries.stageKnockoutBracket(connection, command.tournamentId, command.finalStageId, command.settledAt)
    val championshipFinal = bracket.rounds
      .flatMap(_.matches)
      .find(matchNode => matchNode.lane == KnockoutLane.Championship && matchNode.nextMatchId.isEmpty)
      .getOrElse {
        throw IllegalArgumentException(s"Stage ${command.finalStageId.value} does not contain a championship final")
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
        s"Bronze match must be completed before settlement for stage ${command.finalStageId.value}"
      )
    if finalStage.knockoutRule.exists(_.repechageEnabled) && repechageFinal.exists(!_.completed) then
      throw IllegalArgumentException(
        s"Repechage final must be completed before settlement for stage ${command.finalStageId.value}"
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
      .foreach(existing => riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable.save(connection, existing.supersede(settledAt)))

  private def completeTournamentIfReady(connection: Connection, tournament: Tournament): Unit =
    if tournament.stages.forall(_.status == StageStatus.Completed) && tournament.status != TournamentStatus.Completed then
      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, tournament.complete)

  private def buildSettlementSnapshot(
      connection: Connection,
      command: SettleTournamentCommand,
      ranking: StageRankingSnapshot,
      resolvedPlayers: Vector[PlayerId],
      previousSnapshot: Option[TournamentSettlementSnapshot]
  ): TournamentSettlementSnapshot =
    val effectivePayoutRatios =
      if command.payoutRatios.nonEmpty then command.payoutRatios
      else TournamentRuntimeDefaults.settlementPayoutRatios
    val netPrizePool = command.prizePool - command.houseFeeAmount
    val baseAwards = allocatePrizePool(netPrizePool, effectivePayoutRatios, resolvedPlayers.size)
    val rankingByPlayer = ranking.entries.map(entry => entry.playerId -> entry).toMap
    val adjustmentsByPlayer = command.adjustments.groupBy(_.playerId)
    val championId = resolvedPlayers.headOption.getOrElse {
      throw IllegalArgumentException(s"Stage ${command.finalStageId.value} does not contain any ranked players")
    }
    val revision = previousSnapshot.map(_.revision + 1).getOrElse(1)

    TournamentSettlementSnapshot(
      id = IdGenerator.settlementSnapshotId(),
      tournamentId = command.tournamentId,
      stageId = command.finalStageId,
      revision = revision,
      status =
        if command.finalizeSettlement then TournamentSettlementStatus.Finalized
        else TournamentSettlementStatus.Draft,
      generatedAt = command.settledAt,
      finalizedAt = if command.finalizeSettlement then Some(command.settledAt) else None,
      supersedesSettlementId = previousSnapshot.map(_.id),
      championId = championId,
      prizePool = command.prizePool,
      houseFeeAmount = command.houseFeeAmount,
      netPrizePool = netPrizePool,
      clubShareRatio = command.clubShareRatio,
      adjustments = command.adjustments,
      entries = buildSettlementEntries(
        connection = connection,
        command = command,
        resolvedPlayers = resolvedPlayers,
        baseAwards = baseAwards,
        rankingByPlayer = rankingByPlayer,
        adjustmentsByPlayer = adjustmentsByPlayer
      ),
      summary =
        s"Champion ${championId.value} settled from stage ${command.finalStageId.value} " +
          s"(revision $revision, status ${if command.finalizeSettlement then "finalized" else "draft"}) " +
          s"with gross pool ${command.prizePool} and net pool $netPrizePool."
    )

  private def buildSettlementEntries(
      connection: Connection,
      command: SettleTournamentCommand,
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
      val clubId = PlayerTable
        .findById(connection, playerId)
        .flatMap(_.boundClubIds.headOption)
      val clubShareAmount =
        if clubId.nonEmpty then math.floor(netAwardAmount.toDouble * command.clubShareRatio).toLong
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
      command: SettleTournamentCommand,
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
                aggregateId = command.tournamentId.value,
                eventType = "TournamentSettlementRecorded",
                occurredAt = command.settledAt,
                actorId = command.actor.playerId,
                details = Map(
                  "stageId" -> command.finalStageId.value,
                  "championId" -> savedSnapshot.championId.value,
                  "prizePool" -> command.prizePool.toString,
                  "netPrizePool" -> savedSnapshot.netPrizePool.toString,
                  "houseFeeAmount" -> command.houseFeeAmount.toString,
                  "clubShareRatio" -> command.clubShareRatio.toString,
                  "revision" -> savedSnapshot.revision.toString,
                  "status" -> savedSnapshot.status.toString
                ),
                note = command.note.orElse(Some(savedSnapshot.summary))
              )
            ),
          domainEvents = savedSnapshot =>
            Vector(TournamentSettlementRecorded(savedSnapshot, command.settledAt))
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
