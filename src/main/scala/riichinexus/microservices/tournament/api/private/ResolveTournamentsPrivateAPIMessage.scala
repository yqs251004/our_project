package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.TournamentId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.Tournament
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import upickle.default.*

final case class ResolveTournamentsPrivateAPIMessage(
    tournamentIds: Vector[TournamentId]
) extends APIMessage[Vector[Tournament]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Tournament]] =
    for
      tournaments <- IO.blocking(resolveTournaments(context))
    yield tournaments

  private def resolveTournaments(context: ApiPlanContext): Vector[Tournament] =
    val distinctIds = tournamentIds.distinct
    val prefetched = TournamentTable.findByIds(context.connection, distinctIds)
      .map(tournament => tournament.id -> tournament)
      .toMap

    distinctIds.flatMap { id =>
      prefetched.get(id).orElse(TournamentTable.findById(context.connection, id))
    }
