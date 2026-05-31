package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.Tournament
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import upickle.default.*

final case class DeclineClubTournamentPrivateAPIMessage(
    tournamentId: TournamentId,
    clubId: ClubId
) extends APIMessage[Unit] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Unit] =
    for
      _ <- IO.blocking(declineTournament(context))
    yield ()

  private def declineTournament(context: ApiPlanContext): Unit =
    TournamentTable.findById(context.connection, tournamentId).foreach { tournament =>
      ensureClubTracked(tournament)
      TournamentTable.save(context.connection, TournamentFunctions.removeClub(tournament, clubId))
      ()
    }

  private def ensureClubTracked(tournament: Tournament): Unit =
    val trackedParticipation =
      tournament.participatingClubs.contains(clubId) ||
        tournament.whitelist.exists(_.clubId.contains(clubId))
    if !trackedParticipation then
      throw IllegalArgumentException(
        s"Club ${clubId.value} is not participating in tournament ${tournamentId.value}"
      )
