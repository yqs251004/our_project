package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.ClubId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.Tournament
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import upickle.default.*

final case class ListClubTournamentsPrivateAPIMessage(
    clubId: ClubId
) extends APIMessage[Vector[Tournament]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Tournament]] =
    for
      tournaments <- IO.blocking(TournamentTable.findByClub(context.connection, clubId))
    yield tournaments
