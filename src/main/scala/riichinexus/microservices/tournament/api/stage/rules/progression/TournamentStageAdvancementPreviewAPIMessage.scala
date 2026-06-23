package riichinexus.microservices.tournament.api.stage.rules.progression
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.rules.TournamentStageQueries

import riichinexus.microservices.tournament.objects.stage.rules.progression.StageAdvancementSnapshot
/** 预览赛事阶段晋级结果。 */
final case class TournamentStageAdvancementPreviewAPIMessage(tournamentId: String, stageId: String) extends APIMessage[StageAdvancementSnapshot]:

  override def plan(context: ApiPlanContext): IO[StageAdvancementSnapshot] =
    for
      requestedTournamentId <- IO.blocking(TournamentId(tournamentId))
      requestedStageId <- IO.blocking(TournamentStageId(stageId))
      snapshot <- TournamentStageQueries.stageAdvancementPreview(context.connection, requestedTournamentId, requestedStageId)
    yield snapshot
