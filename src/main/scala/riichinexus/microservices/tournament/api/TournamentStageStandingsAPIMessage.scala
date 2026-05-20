package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import upickle.default.*

final case class TournamentStageStandingsAPIMessage(tournamentId: String, stageId: String) extends APIMessage[StageRankingSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageRankingSnapshot] =
    IO {
      context.support.tournamentModule.stageQueries.stageStandings(TournamentId(tournamentId), TournamentStageId(stageId))
    }
