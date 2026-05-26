package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.RuntimeDictionary
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import upickle.default.*

final case class TournamentSettleAPIMessage(tournamentId: String, request: SettleTournamentRequest) extends APIMessage[TournamentSettlementView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      actor <- IO(context.principal(request.operator))
      settledAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = SettleTournamentCommand(
        tournamentId = TournamentId(tournamentId),
        request = request,
        actor = actor,
        settledAt = settledAt
      )
      snapshot <- IO {
        module.transactionManager.inTransaction {
          settleTournament(context.connection, module, command)
        }
      }
    yield TournamentSettlementView.fromDomain(snapshot)

  private def settleTournament(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: SettleTournamentCommand
  ): TournamentSettlementSnapshot =
    validateSettlementRequest(command.request)

    val tournament = requireTournament(module, command.tournamentId)
    val finalStage = requireStage(tournament, command.request.stageId)
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )

    val ranking = module.stageQueries.stageStandings(
      connection,
      command.tournamentId,
      command.request.stageId,
      command.settledAt
    )
    val resolvedPlayers =
      resolveSettlementPlayers(connection, module, command, finalStage, ranking)
    val previousSnapshot =
      module.tournamentSettlementRepository.findByTournamentAndStage(command.tournamentId, command.request.stageId)

    supersedePreviousSnapshot(module, previousSnapshot, command.settledAt)
    completeTournamentIfReady(module, tournament)

    val snapshot = buildSettlementSnapshot(
      module = module,
      connection = connection,
      command = command,
      ranking = ranking,
      resolvedPlayers = resolvedPlayers,
      previousSnapshot = previousSnapshot
    )
    commitSettlement(module, command, snapshot)

  private def validateSettlementRequest(request: SettleTournamentRequest): Unit =
    require(request.prizePool >= 0L, "Prize pool must be non-negative")
    require(request.houseFeeAmount >= 0L, "House fee amount must be non-negative")
    require(request.houseFeeAmount <= request.prizePool, "House fee amount cannot exceed prize pool")
    require(
      request.clubShareRatio >= 0.0 && request.clubShareRatio <= 1.0,
      "Club share ratio must be between 0.0 and 1.0"
    )

  private def requireTournament(
      module: TournamentModuleContext,
      tournamentId: TournamentId
  ): Tournament =
    module.tournamentRepository
      .findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def resolveSettlementPlayers(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: SettleTournamentCommand,
      finalStage: TournamentStage,
      ranking: StageRankingSnapshot
  ): Vector[PlayerId] =
    if isKnockoutStage(finalStage) then
      resolveKnockoutSettlementPlayers(connection, module, command, finalStage, ranking)
    else ranking.entries.map(_.playerId)

  private def resolveKnockoutSettlementPlayers(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: SettleTournamentCommand,
      finalStage: TournamentStage,
      ranking: StageRankingSnapshot
  ): Vector[PlayerId] =
    val bracket =
      module.stageQueries.stageKnockoutBracket(connection, command.tournamentId, command.request.stageId, command.settledAt)
    val championshipFinal = bracket.rounds
      .flatMap(_.matches)
      .find(matchNode => matchNode.lane == KnockoutLane.Championship && matchNode.nextMatchId.isEmpty)
      .getOrElse {
        throw IllegalArgumentException(s"Stage ${command.request.stageId.value} does not contain a championship final")
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
        s"Bronze match must be completed before settlement for stage ${command.request.stageId.value}"
      )
    if finalStage.knockoutRule.exists(_.repechageEnabled) && repechageFinal.exists(!_.completed) then
      throw IllegalArgumentException(
        s"Repechage final must be completed before settlement for stage ${command.request.stageId.value}"
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
      module: TournamentModuleContext,
      previousSnapshot: Option[TournamentSettlementSnapshot],
      settledAt: Instant
  ): Unit =
    previousSnapshot
      .filter(_.status != TournamentSettlementStatus.Superseded)
      .foreach(existing => module.tournamentSettlementRepository.save(existing.supersede(settledAt)))

  private def completeTournamentIfReady(module: TournamentModuleContext, tournament: Tournament): Unit =
    if tournament.stages.forall(_.status == StageStatus.Completed) && tournament.status != TournamentStatus.Completed then
      module.tournamentRepository.save(tournament.complete)

  private def buildSettlementSnapshot(
      module: TournamentModuleContext,
      connection: java.sql.Connection,
      command: SettleTournamentCommand,
      ranking: StageRankingSnapshot,
      resolvedPlayers: Vector[PlayerId],
      previousSnapshot: Option[TournamentSettlementSnapshot]
  ): TournamentSettlementSnapshot =
    val request = command.request
    val effectivePayoutRatios =
      if request.payoutRatios.nonEmpty then request.payoutRatios
      else RuntimeDictionary.currentSettlementPayoutRatios(module.globalDictionaryRepository)
    val netPrizePool = request.prizePool - request.houseFeeAmount
    val baseAwards = allocatePrizePool(netPrizePool, effectivePayoutRatios, resolvedPlayers.size)
    val rankingByPlayer = ranking.entries.map(entry => entry.playerId -> entry).toMap
    val adjustments = request.adjustments.map(_.adjustment)
    val adjustmentsByPlayer = adjustments.groupBy(_.playerId)
    val championId = resolvedPlayers.headOption.getOrElse {
      throw IllegalArgumentException(s"Stage ${request.stageId.value} does not contain any ranked players")
    }
    val revision = previousSnapshot.map(_.revision + 1).getOrElse(1)

    TournamentSettlementSnapshot(
      id = IdGenerator.settlementSnapshotId(),
      tournamentId = command.tournamentId,
      stageId = request.stageId,
      revision = revision,
      status =
        if request.finalizeSettlement then TournamentSettlementStatus.Finalized
        else TournamentSettlementStatus.Draft,
      generatedAt = command.settledAt,
      finalizedAt = if request.finalizeSettlement then Some(command.settledAt) else None,
      supersedesSettlementId = previousSnapshot.map(_.id),
      championId = championId,
      prizePool = request.prizePool,
      houseFeeAmount = request.houseFeeAmount,
      netPrizePool = netPrizePool,
      clubShareRatio = request.clubShareRatio,
      adjustments = adjustments,
      entries = buildSettlementEntries(
        connection = connection,
        request = request,
        resolvedPlayers = resolvedPlayers,
        baseAwards = baseAwards,
        rankingByPlayer = rankingByPlayer,
        adjustmentsByPlayer = adjustmentsByPlayer
      ),
      summary =
        s"Champion ${championId.value} settled from stage ${request.stageId.value} " +
          s"(revision $revision, status ${if request.finalizeSettlement then "finalized" else "draft"}) " +
          s"with gross pool ${request.prizePool} and net pool $netPrizePool."
    )

  private def buildSettlementEntries(
      connection: java.sql.Connection,
      request: SettleTournamentRequest,
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
        if clubId.nonEmpty then math.floor(netAwardAmount.toDouble * request.clubShareRatio).toLong
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
      module: TournamentModuleContext,
      command: SettleTournamentCommand,
      snapshot: TournamentSettlementSnapshot
  ): TournamentSettlementSnapshot =
    DomainChangeInterpreter
      .auditAndEvents(module.transactionManager, module.auditEventRepository, module.eventBus)
      .commitWithinTransaction(
        DomainChange(
          aggregate = snapshot,
          persist = module.tournamentSettlementRepository.save,
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
                  "stageId" -> command.request.stageId.value,
                  "championId" -> savedSnapshot.championId.value,
                  "prizePool" -> command.request.prizePool.toString,
                  "netPrizePool" -> savedSnapshot.netPrizePool.toString,
                  "houseFeeAmount" -> command.request.houseFeeAmount.toString,
                  "clubShareRatio" -> command.request.clubShareRatio.toString,
                  "revision" -> savedSnapshot.revision.toString,
                  "status" -> savedSnapshot.status.toString
                ),
                note = command.request.note.orElse(Some(savedSnapshot.summary))
              )
            ),
          domainEvents = savedSnapshot =>
            Vector(TournamentSettlementRecorded(savedSnapshot, command.settledAt))
        )
      )

  private def isKnockoutStage(stage: TournamentStage): Boolean =
    stage.format == StageFormat.Knockout ||
      stage.format == StageFormat.Finals ||
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

  private final case class SettleTournamentCommand(
      tournamentId: TournamentId,
      request: SettleTournamentRequest,
      actor: AccessPrincipal,
      settledAt: Instant
  )
