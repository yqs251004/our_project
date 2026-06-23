package riichinexus.microservices.tournament.api.stage.rules.knockout
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.rules.TournamentStageQueries

import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutBracketSnapshot
/** 获取赛事阶段的淘汰赛签表。 */
final case class TournamentStageKnockoutBracketAPIMessage(tournamentId: String, stageId: String) extends APIMessage[KnockoutBracketSnapshot]:

  override def plan(context: ApiPlanContext): IO[KnockoutBracketSnapshot] =
    for
      requestedTournamentId <- IO.blocking(TournamentId(tournamentId))
      requestedStageId <- IO.blocking(TournamentStageId(stageId))
      snapshot <- TournamentStageQueries.stageKnockoutBracket(context.connection, requestedTournamentId, requestedStageId)
    yield snapshot
