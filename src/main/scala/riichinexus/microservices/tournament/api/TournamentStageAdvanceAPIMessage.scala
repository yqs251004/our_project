package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.objects.{AdvancementRuleType, TournamentFormat}

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentStageAdvanceAPIMessage(tournamentId: String, stageId: String, request: AdvanceKnockoutStageRequest) extends APIMessage[Vector[riichinexus.microservices.tournament.objects.Table]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[riichinexus.microservices.tournament.objects.Table]] =
    for
      actor <- IO.blocking(request.operator.map(context.principal).getOrElse(AccessPrincipal.system))
      at <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = AdvanceKnockoutStageCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = actor,
        at = at
      )
      tables <- IO.blocking {
        module.transactionManager.inTransaction {
          advanceStage(context.connection, module, command)
        }
      }
    yield tables.map(riichinexus.microservices.tournament.objects.Table.fromDomain)

  private def advanceStage(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: AdvanceKnockoutStageCommand
  ): Vector[Table] =
    val tournament = riichinexus.microservices.tournament.tables.tournament.TournamentTable
      .findById(connection, command.tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${command.tournamentId.value} was not found"))
    val stage = tournament.stages
      .find(_.id == command.stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${command.stageId.value} was not found"))
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )
    ensureKnockoutStage(stage, command.stageId)
    KnockoutStageCoordinator.materializeUnlockedTables(
      connection,
      module.transactionManager,
      command.tournamentId,
      command.stageId,
      command.at
    )

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
      actor: AccessPrincipal,
      at: Instant
  )
