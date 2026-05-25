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
    for
      input <- IO(resolveInput)
      snapshot <- IO(context.support.tournamentModule.stageQueries.stageAdvancementPreview(input.tournamentId, input.stageId))
    yield StageAdvancementSnapshotResponse.fromDomain(snapshot)

  private def resolveInput: StageQueryInput =
    StageQueryInput(TournamentId(tournamentId), TournamentStageId(stageId))

  private final case class StageQueryInput(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
