package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.TournamentStageCompletionCoordinator
import riichinexus.microservices.tournament.objects.stage.rules.progression.StageAdvancementSnapshot
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 完成赛事阶段并生成晋级快照。 */
final case class TournamentStageCompleteAPIMessage(
    tournamentId: String,
    stageId: String,
    operatorId: Option[String] = None
) extends APIMessage[StageAdvancementSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageAdvancementSnapshot] =
    for
      completedAt <- IO.realTimeInstant
      actor <- operatorId.filter(_.nonEmpty).map(PlayerId(_)).map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context)).getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))
      parsedTournamentId = TournamentId(tournamentId)
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.ManageTournamentStages, tournamentId = Some(parsedTournamentId)).plan(context)
      advancement <- TournamentStageCompletionCoordinator.completeStage(
        connection = context.connection,
        tournamentId = parsedTournamentId,
        stageId = TournamentStageId(stageId),
        actor = actor,
        completedAt = completedAt
      ).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield advancement
