package riichinexus.microservices.tournament.api.stage.rules.progression
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import riichinexus.microservices.tournament.objects.stage.rules.progression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.competition.TournamentFormat

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.stage.functions.rules.knockout.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.microservices.tournament.objects.stage.table.TournamentTableView
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable

/** 推进淘汰阶段并物化已解锁的牌桌。 */
final case class TournamentStageAdvanceAPIMessage(tournamentId: String, stageId: String, operatorId: Option[String] = None) extends APIMessage[Vector[TournamentTableView]]:

  override def plan(context: ApiPlanContext): IO[Vector[TournamentTableView]] =
    for
      actor <- operatorId.filter(_.nonEmpty).map(PlayerId(_)).map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context)).getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))
      at <- IO.realTimeInstant
      requestedTournamentId = TournamentId(tournamentId)
      requestedStageId = TournamentStageId(stageId)
      tables <- advanceStage(context, requestedTournamentId, requestedStageId, actor, at)
    yield tables.map(TournamentViewFunctions.tableView)

  private def advanceStage(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipalPrivateView,
      at: Instant
  ): IO[Vector[Table]] =
    for
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      ).plan(context)
      _ <- IO.blocking {
        val tournament = TournamentTable
          .findById(context.connection, tournamentId)
          .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
        val stage = tournament.stages
          .find(_.id == stageId)
          .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))
        ensureKnockoutStage(stage, stageId)
      }
      tables <- KnockoutStageCoordinator.materializeUnlockedTables(
        context.connection,
        tournamentId,
        stageId,
        at
      )
    yield tables

  private def ensureKnockoutStage(stage: TournamentStage, stageId: TournamentStageId): Unit =
    val isKnockoutStage =
      stage.format == TournamentFormat.Knockout ||
        stage.format == TournamentFormat.Finals ||
        stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination
    if !isKnockoutStage then
      throw IllegalArgumentException(
        s"Stage ${stageId.value} is not configured as a knockout stage"
      )

