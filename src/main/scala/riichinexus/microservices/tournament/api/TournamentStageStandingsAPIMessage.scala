package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.rules.TournamentStageQueries

import riichinexus.microservices.tournament.objects.stage.ranking.StageRankingSnapshot
import upickle.default.ReadWriter

/** 获取赛事阶段排名。 */
final case class TournamentStageStandingsAPIMessage(tournamentId: String, stageId: String) extends APIMessage[StageRankingSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageRankingSnapshot] =
    for
      input <- IO.blocking(resolveInput)
      snapshot <- TournamentStageQueries.stageStandings(context.connection, input.tournamentId, input.stageId)
    yield snapshot

  private def resolveInput: StageQueryInput =
    StageQueryInput(TournamentId(tournamentId), TournamentStageId(stageId))

  private final case class StageQueryInput(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
