package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
/** 供 club 公共 API 校验后记录俱乐部拒绝赛事邀请。 */
final case class RecordClubTournamentDeclinePrivateAPIMessage(
    tournamentId: TournamentId,
    clubId: ClubId
) extends APIMessage[Unit]:

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
