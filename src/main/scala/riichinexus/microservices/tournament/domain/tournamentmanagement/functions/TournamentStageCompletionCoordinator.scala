package riichinexus.microservices.tournament.domain.tournamentmanagement.functions
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.player.api.`private`.ResolvePlayersPrivateAPIMessage

import riichinexus.microservices.auth.domain.authorization.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

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
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRuleType, StageAdvancementSnapshot}
import riichinexus.microservices.tournament.objects.tablemanagement.TableStatus
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat

import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
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
import riichinexus.microservices.auth.domain.AuthorizationPolicy
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.api.`private`.ResolveClubsPrivateAPIMessage
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.{TournamentFunctions, TournamentStageFunctions}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

final class TournamentStageCompletionCoordinator(
    authorizationService: AuthorizationPolicy
):
  def completeStage(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      completedAt: Instant
  ): IO[Option[StageAdvancementSnapshot]] =
    IO.blocking(TournamentTable.findById(connection, tournamentId)).flatMap {
      case Some(tournament) =>
        for
          stageAndTables <- IO.blocking {
            AuthorizationPolicyFunctions.requirePermission(authorizationService,
              actor,
              Permission.ManageTournamentStages,
              tournamentId = Some(tournamentId)
            )

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
      participants: Vector[Player],
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
