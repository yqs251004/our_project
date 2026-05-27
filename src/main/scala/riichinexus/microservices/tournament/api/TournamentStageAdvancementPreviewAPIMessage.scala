package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.TournamentStageQueries
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.{StageAdvancementSnapshot as StageAdvancementSnapshotResponse}
import upickle.default.*

final case class TournamentStageAdvancementPreviewAPIMessage(tournamentId: String, stageId: String) extends APIMessage[StageAdvancementSnapshotResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageAdvancementSnapshotResponse] =
    for
      input <- IO(resolveInput)
      snapshot <- IO(TournamentStageQueries.stageAdvancementPreview(context.connection, input.tournamentId, input.stageId))
    yield StageAdvancementSnapshotResponse.fromDomain(snapshot)

  private def resolveInput: StageQueryInput =
    StageQueryInput(TournamentId(tournamentId), TournamentStageId(stageId))

  private final case class StageQueryInput(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
