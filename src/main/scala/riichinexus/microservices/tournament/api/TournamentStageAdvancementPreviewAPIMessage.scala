package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.rules.TournamentStageQueries

import riichinexus.microservices.tournament.objects.stage.rules.progression.StageAdvancementSnapshot
import upickle.default.ReadWriter

/** 预览赛事阶段晋级结果。 */
final case class TournamentStageAdvancementPreviewAPIMessage(tournamentId: String, stageId: String) extends APIMessage[StageAdvancementSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageAdvancementSnapshot] =
    for
      input <- IO.blocking(resolveInput)
      snapshot <- TournamentStageQueries.stageAdvancementPreview(context.connection, input.tournamentId, input.stageId)
    yield snapshot

  private def resolveInput: StageQueryInput =
    StageQueryInput(TournamentId(tournamentId), TournamentStageId(stageId))

  private final case class StageQueryInput(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
