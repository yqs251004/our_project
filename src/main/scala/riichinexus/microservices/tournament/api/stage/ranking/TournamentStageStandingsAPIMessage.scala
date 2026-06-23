package riichinexus.microservices.tournament.api.stage.ranking
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.rules.TournamentStageQueries

import riichinexus.microservices.tournament.objects.stage.ranking.StageRankingSnapshot
/** 获取赛事阶段排名。 */
final case class TournamentStageStandingsAPIMessage(tournamentId: String, stageId: String) extends APIMessage[StageRankingSnapshot]:

  override def plan(context: ApiPlanContext): IO[StageRankingSnapshot] =
    for
      requestedTournamentId <- IO.blocking(TournamentId(tournamentId))
      requestedStageId <- IO.blocking(TournamentStageId(stageId))
      snapshot <- TournamentStageQueries.stageStandings(context.connection, requestedTournamentId, requestedStageId)
    yield snapshot
