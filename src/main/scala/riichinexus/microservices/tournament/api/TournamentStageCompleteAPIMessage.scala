package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.domain.{StageLineupResolver, TournamentRuleEngine, TournamentStageQueries}
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentStageCompleteAPIMessage(tournamentId: String, stageId: String, request: CompleteStageRequest) extends APIMessage[riichinexus.microservices.tournament.objects.StageAdvancementSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[riichinexus.microservices.tournament.objects.StageAdvancementSnapshot] =
    for
      completedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = CompleteStageCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = request.operator.map(context.principal).getOrElse(AccessPrincipal.system),
        completedAt = completedAt
      )
      advancement <- IO {
        module.transactionManager
          .inTransaction {
            completeStage(context.connection, module, command)
          }
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield riichinexus.microservices.tournament.objects.StageAdvancementSnapshot.fromDomain(advancement)

  private def completeStage(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: CompleteStageCommand
  ): Option[StageAdvancementSnapshot] =
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(command.tournamentId)
      )

      val stage = requireStage(tournament, command.stageId)
      val stageTables = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, command.tournamentId, command.stageId)
      ensureStageCanComplete(connection, module, tournament, stage, stageTables, command.completedAt)

      val advancement =
        TournamentStageQueries.stageAdvancementPreview(connection, command.tournamentId, command.stageId, command.completedAt)

      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, tournament.updateStage(command.stageId, _.complete))
      advancement
    }

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def ensureStageCanComplete(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      stageTables: Vector[Table],
      completedAt: Instant
  ): Unit =
    ensureAllTablesMaterialized(stage, stageTables)
    ensureAllTablesArchived(stage, stageTables)
    if !isKnockoutStage(stage) then
      ensureNonKnockoutRoundsComplete(connection, module, tournament, stage, stageTables, completedAt)

  private def ensureAllTablesMaterialized(stage: TournamentStage, stageTables: Vector[Table]): Unit =
    if stageTables.size != stage.scheduledTableIds.size then
      throw IllegalArgumentException(
        s"Stage ${stage.id.value} cannot complete before every scheduled table is materialized"
      )

  private def ensureAllTablesArchived(stage: TournamentStage, stageTables: Vector[Table]): Unit =
    if stageTables.exists(_.status != TableStatus.Archived) then
      throw IllegalArgumentException(
        s"Stage ${stage.id.value} cannot complete while tables are still active or under appeal"
      )

  private def ensureNonKnockoutRoundsComplete(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      stageTables: Vector[Table],
      completedAt: Instant
  ): Unit =
    val participants = resolveParticipants(connection, module, tournament, stage)
    val records = riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable.findByTournamentAndStage(connection, tournament.id, stage.id)
    val effectiveRoundLimit = StageLineupResolver.effectiveRoundLimit(stage)
    val requiredTablesPerRound =
      expectedTablesPerRound(
        module = module,
        tournament = tournament,
        stage = stage,
        participants = participants,
        records = records,
        at = completedAt
      )
    val roundCounts = stageTables.groupBy(_.stageRoundNumber).view.mapValues(_.size).toMap
    val missingRounds = (1 to effectiveRoundLimit).filter(roundNumber =>
      roundCounts.getOrElse(roundNumber, 0) != requiredTablesPerRound
    )

    if stage.pendingTablePlans.nonEmpty || stage.currentRound < effectiveRoundLimit || missingRounds.nonEmpty then
      throw IllegalArgumentException(
        s"Stage ${stage.id.value} cannot complete before all $effectiveRoundLimit rounds are fully scheduled and archived"
      )

  private def isKnockoutStage(stage: TournamentStage): Boolean =
    stage.format == StageFormat.Knockout ||
      stage.format == StageFormat.Finals ||
      stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

  private def resolveParticipants(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val clubsById = riichinexus.microservices.club.tables.club.ClubTable.findByIds(connection, 
      (tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct
    ).map(club => club.id -> club).toMap

    val fallbackPlayerIds =
      val registeredClubMembers = tournament.participatingClubs.flatMap { clubId =>
        clubsById.get(clubId).toVector.flatMap(_.members)
      }
      val whitelistedPlayers = tournament.whitelist.flatMap(_.playerId)
      val whitelistedClubMembers = tournament.whitelist.flatMap { entry =>
        entry.clubId.toVector.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members))
      }

      (tournament.participatingPlayers ++ whitelistedPlayers ++ registeredClubMembers ++ whitelistedClubMembers).distinct

    val playersById = PlayerTable
      .findByIds(connection, (stage.lineupSubmissions.flatMap(_.seats.map(_.playerId)) ++ fallbackPlayerIds).distinct)
      .map(player => player.id -> player)
      .toMap
    val stagePlayerIds = StageLineupResolver.resolveEligiblePlayers(stage, playersById.get)

    val targetPlayerIds =
      StageLineupResolver.resolveTargetPlayerIds(tournament, stagePlayerIds, fallbackPlayerIds)

    targetPlayerIds.flatMap { playerId =>
      playersById.get(playerId).filter(_.status == PlayerStatus.Active)
    }

  private def expectedTablesPerRound(
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      records: Vector[MatchRecord],
      at: Instant
  ): Int =
    stage.format match
      case StageFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(
          module = module,
          tournament = tournament,
          stage = stage,
          participants = participants,
          history = records,
          roundNumber = math.max(1, stage.currentRound),
          at = at
        )
        selectedPlayers.size / 4
      case _ =>
        participants.size / 4

  private def selectCustomStageParticipants(
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      history: Vector[MatchRecord],
      roundNumber: Int,
      at: Instant
  ): Vector[Player] =
    val maxTables = math.max(1, math.min(participants.size / 4, customStageTableCount(stage, participants.size)))
    val targetParticipants = maxTables * 4
    val rankingOrder =
      if history.nonEmpty then
        val ranking = TournamentRuleEngine.buildStageRanking(
          tournament,
          stage,
          participants.map(_.id),
          history,
          at
        )
        ranking.entries.flatMap(entry => participants.find(_.id == entry.playerId))
      else Vector.empty

    val seededOrder =
      if rankingOrder.nonEmpty then rankingOrder
      else
        participants.sortBy(player => (-player.elo, player.nickname, player.id.value))

    val rotatedOrder =
      if seededOrder.isEmpty then seededOrder
      else rotateVector(seededOrder, (roundNumber - 1) % seededOrder.size)

    rotatedOrder.take(targetParticipants)

  private def customStageTableCount(
      stage: TournamentStage,
      participantCount: Int
  ): Int =
    val availableTables = participantCount / 4
    require(availableTables >= 1, s"Stage ${stage.id.value} needs at least one full table")
    stage.advancementRule.targetTableCount match
      case Some(value) =>
        require(value >= 1, s"Stage ${stage.id.value} targetTableCount must be positive")
        require(value <= availableTables, s"Stage ${stage.id.value} targetTableCount exceeds available tables")
        value
      case None =>
        availableTables

  private def rotateVector[A](values: Vector[A], shift: Int): Vector[A] =
    if values.isEmpty then values
    else
      val normalized = math.floorMod(shift, values.size)
      values.drop(normalized) ++ values.take(normalized)

  private final case class CompleteStageCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      completedAt: Instant
  )
