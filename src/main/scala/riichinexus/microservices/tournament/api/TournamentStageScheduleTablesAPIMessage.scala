package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.{TournamentOperationViewAssembler, TournamentStageTableScheduler}
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.system.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentStageScheduleTablesAPIMessage(
    tournamentId: String,
    stageId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val stageIdValue = TournamentStageId(stageId)
      val actor = OperatorRequest(operatorId.filter(_.nonEmpty)).operator
        .map(context.support.principal)
        .getOrElse(AccessPrincipal.system)

      val scheduledTables = module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentIdValue)
        )

        TournamentStageTableScheduler.schedule(
          module = module,
          tournamentId = tournamentIdValue,
          stageId = stageIdValue
        )
      }

      TournamentOperationViewAssembler
        .mutationView(module, tournamentIdValue, scheduledTables)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
