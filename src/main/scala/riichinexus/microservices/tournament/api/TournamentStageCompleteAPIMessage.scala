package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.StageAdvancementSnapshot
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TournamentStageCompleteAPIMessage(
    tournamentId: String,
    stageId: String,
    operatorId: Option[String] = None
) extends APIMessage[StageAdvancementSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageAdvancementSnapshot] =
    for
      completedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      actor = operatorId.filter(_.nonEmpty).map(PlayerId(_)).map(context.principal).getOrElse(AccessPrincipal.system)
      advancement <- IO.blocking {
        module.transactionManager
          .inTransaction {
            module.stageCompletionCoordinator.completeStage(
              connection = context.connection,
              tournamentId = TournamentId(tournamentId),
              stageId = TournamentStageId(stageId),
              actor = actor,
              completedAt = completedAt
            )
          }
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield advancement
