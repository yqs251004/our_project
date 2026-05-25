package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{StageAdvancementSnapshot as StageAdvancementSnapshotResponse}
import upickle.default.*

final case class TournamentStageAdvancementPreviewAPIMessage(tournamentId: String, stageId: String) extends APIMessage[StageAdvancementSnapshotResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageAdvancementSnapshotResponse] =
    IO {
      StageAdvancementSnapshotResponse.fromDomain(
        context.support.tournamentModule.stageQueries.stageAdvancementPreview(TournamentId(tournamentId), TournamentStageId(stageId))
      )
    }
