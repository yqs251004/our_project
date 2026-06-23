package riichinexus.microservices.tournament.api.stage.rules.progression
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.TournamentStageCompletionCoordinator
import riichinexus.microservices.tournament.objects.stage.rules.progression.StageAdvancementSnapshot
import riichinexus.system.json.JsonCodecs.given
/** 完成赛事阶段并生成晋级快照。 */
final case class TournamentStageCompleteAPIMessage(
    tournamentId: String,
    stageId: String,
    operatorId: Option[String] = None
) extends APIMessage[StageAdvancementSnapshot]:

  override def plan(context: ApiPlanContext): IO[StageAdvancementSnapshot] =
    for
      completedAt <- IO.realTimeInstant
      actor <- operatorId.filter(_.nonEmpty).map(PlayerId(_)).map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context)).getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))
      requestedTournamentId = TournamentId(tournamentId)
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ManageTournamentStages, tournamentId = Some(requestedTournamentId)).plan(context)
      advancement <- TournamentStageCompletionCoordinator.completeStage(
        connection = context.connection,
        tournamentId = requestedTournamentId,
        stageId = TournamentStageId(stageId),
        actor = actor,
        completedAt = completedAt
      ).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield advancement
