package riichinexus.microservices.tournament.api
import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import riichinexus.microservices.tournament.objects.stage.rules.progression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.competition.TournamentFormat

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.stage.functions.rules.knockout.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.microservices.tournament.objects.stage.table.apiTypes.TournamentTableView

/** 推进淘汰阶段并物化已解锁的牌桌。 */
final case class TournamentStageAdvanceAPIMessage(tournamentId: String, stageId: String, operatorId: Option[String] = None) extends APIMessage[Vector[TournamentTableView]]:

  override def plan(context: ApiPlanContext): IO[Vector[TournamentTableView]] =
    for
      actor <- operatorId.filter(_.nonEmpty).map(PlayerId(_)).map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context)).getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))
      at <- IO.realTimeInstant
      command = AdvanceKnockoutStageCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = actor,
        at = at
      )
      tables <- advanceStage(context, command)
    yield tables.map(TournamentViewFunctions.tableView)

  private def advanceStage(
      context: ApiPlanContext,
      command: AdvanceKnockoutStageCommand
  ): IO[Vector[Table]] =
    for
      _ <- RequirePermissionPrivateAPIMessage(
        command.actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(command.tournamentId)
      ).plan(context)
      _ <- IO.blocking {
        val tournament = riichinexus.microservices.tournament.tables.tournaments.TournamentTable
          .findById(context.connection, command.tournamentId)
          .getOrElse(throw NoSuchElementException(s"Tournament ${command.tournamentId.value} was not found"))
        val stage = tournament.stages
          .find(_.id == command.stageId)
          .getOrElse(throw NoSuchElementException(s"Stage ${command.stageId.value} was not found"))
        ensureKnockoutStage(stage, command.stageId)
      }
      tables <- KnockoutStageCoordinator.materializeUnlockedTables(
        context.connection,
        command.tournamentId,
        command.stageId,
        command.at
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

  private final case class AdvanceKnockoutStageCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipalPrivateView,
      at: Instant
  )
