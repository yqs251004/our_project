package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.TournamentStageQueries
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import upickle.default.*

final case class TournamentStageKnockoutBracketAPIMessage(tournamentId: String, stageId: String) extends APIMessage[KnockoutBracketSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[KnockoutBracketSnapshot] =
    for
      input <- IO.blocking(resolveInput)
      snapshot <- IO.blocking(TournamentStageQueries.stageKnockoutBracket(context.connection, input.tournamentId, input.stageId))
    yield snapshot

  private def resolveInput: StageQueryInput =
    StageQueryInput(TournamentId(tournamentId), TournamentStageId(stageId))

  private final case class StageQueryInput(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
