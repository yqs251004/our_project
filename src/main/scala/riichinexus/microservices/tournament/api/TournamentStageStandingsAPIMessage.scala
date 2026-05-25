package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{StageRankingSnapshot as StageRankingSnapshotResponse}
import upickle.default.*

final case class TournamentStageStandingsAPIMessage(tournamentId: String, stageId: String) extends APIMessage[StageRankingSnapshotResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageRankingSnapshotResponse] =
    for
      input <- IO(resolveInput)
      snapshot <- IO(context.support.tournamentModule.stageQueries.stageStandings(input.tournamentId, input.stageId))
    yield StageRankingSnapshotResponse.fromDomain(snapshot)

  private def resolveInput: StageQueryInput =
    StageQueryInput(TournamentId(tournamentId), TournamentStageId(stageId))

  private final case class StageQueryInput(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
