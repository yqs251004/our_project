package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import upickle.default.*

final case class TournamentStageKnockoutBracketAPIMessage(tournamentId: String, stageId: String) extends APIMessage[KnockoutBracketSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[KnockoutBracketSnapshot] =
    IO {
      context.support.tournamentModule.stageQueries.stageKnockoutBracket(TournamentId(tournamentId), TournamentStageId(stageId))
    }
