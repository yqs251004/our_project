package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.CompleteStageCommand
import riichinexus.microservices.tournament.objects.StageAdvancementSnapshot
import riichinexus.microservices.tournament.objects.apiTypes.CompleteStageRequest
import upickle.default.*

final case class TournamentStageCompleteAPIMessage(
    tournamentId: String,
    stageId: String,
    request: CompleteStageRequest
) extends APIMessage[StageAdvancementSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageAdvancementSnapshot] =
    for
      completedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = CompleteStageCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = request.operator.map(context.principal).getOrElse(AccessPrincipal.system),
        completedAt = completedAt
      )
      advancement <- IO.blocking {
        module.transactionManager
          .inTransaction {
            module.stageCompletionCoordinator.completeStage(context.connection, command)
          }
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield StageAdvancementSnapshot.fromDomain(advancement)
