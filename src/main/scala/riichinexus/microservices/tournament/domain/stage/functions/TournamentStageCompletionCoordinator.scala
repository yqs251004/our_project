package riichinexus.microservices.tournament.domain.stage.functions

import riichinexus.microservices.tournament.domain.stage.functions.lineup.StageLineupResolver
import riichinexus.microservices.tournament.domain.stage.functions.rules.{TournamentRuleEngine, TournamentStageParticipantResolver, TournamentStageQueries}
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRuleType, StageAdvancementSnapshot}
import riichinexus.microservices.tournament.objects.tablemanagement.TableStatus
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat

import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView

import riichinexus.microservices.tournament.domain.stage.model.{Table, TournamentStage}
import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

/** TournamentStageCompletionCoordinator 负责赛事阶段Completion协调器 相关的领域编排、构建或投影计算。 */

private[tournament] object TournamentStageCompletionCoordinator:
  def completeStage(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipalPrivateView,
      completedAt: Instant
  ): IO[Option[StageAdvancementSnapshot]] =
    IO.blocking(TournamentTable.findById(connection, tournamentId)).flatMap {
      case Some(tournament) =>
        for
          stageAndTables <- IO.blocking {
            val stage = requireStage(tournament, stageId)
            val stageTables = TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId)
            ensureAllTablesMaterialized(stage, stageTables)
            ensureAllTablesArchived(stage, stageTables)
            (stage, stageTables)
          }
          (stage, stageTables) = stageAndTables
          _ <- ensureStageCanComplete(connection, tournament, stage, stageTables, completedAt)
          advancement <- TournamentStageQueries.stageAdvancementPreview(connection, tournamentId, stageId, completedAt)
          _ <- IO.blocking(TournamentTable.save(connection, TournamentFunctions.updateStage(tournament, stageId, TournamentStageFunctions.complete)))
        yield Some(advancement)
      case None => IO.pure(None)
    }

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def ensureStageCanComplete(
      connection: Connection,
      tournament: Tournament,
      stage: TournamentStage,
      stageTables: Vector[Table],
      completedAt: Instant
  ): IO[Unit] =
    if !isKnockoutStage(stage) then
      ensureNonKnockoutRoundsComplete(connection, tournament, stage, stageTables, completedAt)
    else IO.unit

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
      connection: Connection,
      tournament: Tournament,
      stage: TournamentStage,
      stageTables: Vector[Table],
      completedAt: Instant
  ): IO[Unit] =
    for
      participants <- TournamentStageParticipantResolver.resolveParticipants(ApiPlanContext(bearerToken = None, connection = connection), tournament, stage)
      _ <- IO.blocking {
    val records = MatchRecordTable.findByTournamentAndStage(connection, tournament.id, stage.id)
    val effectiveRoundLimit = StageLineupResolver.effectiveRoundLimit(stage)
    val requiredTablesPerRound =
      expectedTablesPerRound(
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
      }
    yield ()

  private def isKnockoutStage(stage: TournamentStage): Boolean =
    stage.format == TournamentFormat.Knockout ||
      stage.format == TournamentFormat.Finals ||
      stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

  private def expectedTablesPerRound(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerPrivateView],
      records: Vector[MatchRecord],
      at: Instant
  ): Int =
    stage.format match
      case TournamentFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(
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
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerPrivateView],
      history: Vector[MatchRecord],
      roundNumber: Int,
      at: Instant
  ): Vector[PlayerPrivateView] =
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
      else participants.sortBy(player => (-player.elo, player.nickname, player.id.value))

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
