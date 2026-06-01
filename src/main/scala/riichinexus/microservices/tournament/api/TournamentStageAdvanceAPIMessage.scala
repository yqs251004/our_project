package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentStageAdvanceAPIMessage(tournamentId: String, stageId: String, operatorId: Option[String] = None) extends APIMessage[Vector[TournamentTableView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[TournamentTableView]] =
    for
      actor <- IO.blocking(operatorId.filter(_.nonEmpty).map(PlayerId(_)).map(AuthAccessPrincipalResolver.principal(context, _)).getOrElse(AccessPrincipalFunctions.system))
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
    yield tables.map(TournamentTableView.fromDomain)

  private def advanceStage(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: AdvanceKnockoutStageCommand
  ): Vector[Table] =
    val tournament = riichinexus.microservices.tournament.tables.tournaments.TournamentTable
      .findById(connection, command.tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${command.tournamentId.value} was not found"))
    val stage = tournament.stages
      .find(_.id == command.stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${command.stageId.value} was not found"))
    AuthorizationPolicyFunctions.requirePermission(module.authorizationService, 
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
