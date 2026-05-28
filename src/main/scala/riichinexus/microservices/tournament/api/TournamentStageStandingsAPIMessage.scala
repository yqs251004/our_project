package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.TournamentStageQueries
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.{StageRankingSnapshot as StageRankingSnapshotResponse}
import upickle.default.*

final case class TournamentStageStandingsAPIMessage(tournamentId: String, stageId: String) extends APIMessage[StageRankingSnapshotResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageRankingSnapshotResponse] =
    for
      input <- IO.blocking(resolveInput)
      snapshot <- IO.blocking(TournamentStageQueries.stageStandings(context.connection, input.tournamentId, input.stageId))
    yield StageRankingSnapshotResponse.fromDomain(snapshot)

  private def resolveInput: StageQueryInput =
    StageQueryInput(TournamentId(tournamentId), TournamentStageId(stageId))

  private final case class StageQueryInput(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
