package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.rules.TournamentStageQueries

import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import upickle.default.ReadWriter

/** 获取赛事阶段的淘汰赛签表。 */
final case class TournamentStageKnockoutBracketAPIMessage(tournamentId: String, stageId: String) extends APIMessage[KnockoutBracketSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[KnockoutBracketSnapshot] =
    for
      input <- IO.blocking(resolveInput)
      snapshot <- TournamentStageQueries.stageKnockoutBracket(context.connection, input.tournamentId, input.stageId)
    yield snapshot

  private def resolveInput: StageQueryInput =
    StageQueryInput(TournamentId(tournamentId), TournamentStageId(stageId))

  private final case class StageQueryInput(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
