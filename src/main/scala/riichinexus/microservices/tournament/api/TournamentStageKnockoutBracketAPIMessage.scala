package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{KnockoutBracketSnapshot as KnockoutBracketSnapshotResponse}
import upickle.default.*

final case class TournamentStageKnockoutBracketAPIMessage(tournamentId: String, stageId: String) extends APIMessage[KnockoutBracketSnapshotResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[KnockoutBracketSnapshotResponse] =
    IO {
      KnockoutBracketSnapshotResponse.fromDomain(
        context.support.tournamentModule.stageQueries.stageKnockoutBracket(TournamentId(tournamentId), TournamentStageId(stageId))
      )
    }
