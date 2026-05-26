package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.{KnockoutBracketSnapshot as KnockoutBracketSnapshotResponse}
import upickle.default.*

final case class TournamentStageKnockoutBracketAPIMessage(tournamentId: String, stageId: String) extends APIMessage[KnockoutBracketSnapshotResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[KnockoutBracketSnapshotResponse] =
    for
      input <- IO(resolveInput)
      snapshot <- IO(context.support.tournamentModule.stageQueries.stageKnockoutBracket(context.connection, input.tournamentId, input.stageId))
    yield KnockoutBracketSnapshotResponse.fromDomain(snapshot)

  private def resolveInput: StageQueryInput =
    StageQueryInput(TournamentId(tournamentId), TournamentStageId(stageId))

  private final case class StageQueryInput(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  )
