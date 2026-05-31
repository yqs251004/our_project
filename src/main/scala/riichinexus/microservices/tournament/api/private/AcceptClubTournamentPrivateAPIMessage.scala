package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.Tournament
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import upickle.default.*

final case class AcceptClubTournamentPrivateAPIMessage(
    tournamentId: TournamentId,
    clubId: ClubId
) extends APIMessage[Unit] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Unit] =
    for
      _ <- IO.blocking(acceptTournament(context))
    yield ()

  private def acceptTournament(context: ApiPlanContext): Unit =
    TournamentTable.findById(context.connection, tournamentId).foreach { tournament =>
      ensureClubInvitedOrParticipating(tournament)
      TournamentTable.save(context.connection, TournamentFunctions.registerClub(tournament, clubId))
      ()
    }

  private def ensureClubInvitedOrParticipating(tournament: Tournament): Unit =
    val alreadyParticipating = tournament.participatingClubs.contains(clubId)
    val isWhitelisted = tournament.whitelist.exists(_.clubId.contains(clubId))
    if !alreadyParticipating && !isWhitelisted then
      throw IllegalArgumentException(
        s"Club ${clubId.value} is not invited to tournament ${tournamentId.value}"
      )
