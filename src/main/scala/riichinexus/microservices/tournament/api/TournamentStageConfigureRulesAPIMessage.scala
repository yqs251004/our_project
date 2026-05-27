package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentStageConfigureRulesAPIMessage(tournamentId: String, stageId: String, request: ConfigureStageRulesRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(context.principal(request.operator))
      module = context.support.tournamentModule
      command = ConfigureStageRulesCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = actor,
        request = request
      )
      tournament <- IO {
        module.transactionManager.inTransaction {
          configureStageRules(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def configureStageRules(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: ConfigureStageRulesCommand
  ): Option[Tournament] =
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      val currentStage = requireStage(tournament, command.stageId)
      module.authorizationService.requirePermission(
        command.actor,
        Permission.ConfigureTournamentRules,
        tournamentId = Some(command.tournamentId)
      )
      val configuredStage = buildConfiguredStage(module, currentStage, command.request)
      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, 
        tournament.updateStage(command.stageId, _ => configuredStage)
      )
    }

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def buildConfiguredStage(
      module: TournamentModuleContext,
      currentStage: TournamentStage,
      request: ConfigureStageRulesRequest
  ): TournamentStage =
    val baseStage = currentStage.copy(
      format = request.stageFormat.getOrElse(currentStage.format),
      roundCount = math.max(request.roundCount.getOrElse(currentStage.roundCount), currentStage.currentRound)
    )
    TournamentRuntimeDefaults.normalizeStage(
      baseStage.withRules(
        request.advancementRule,
        request.swissRule,
        request.knockoutRule,
        request.schedulingPoolSize.getOrElse(baseStage.schedulingPoolSize)
      )
    )

  private final case class ConfigureStageRulesCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      request: ConfigureStageRulesRequest
  )
