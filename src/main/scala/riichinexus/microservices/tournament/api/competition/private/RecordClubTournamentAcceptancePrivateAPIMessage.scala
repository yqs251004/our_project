package riichinexus.microservices.tournament.api.competition.`private`
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
/** 供 club 公共 API 校验后记录俱乐部接受赛事邀请。 */
final case class RecordClubTournamentAcceptancePrivateAPIMessage(
    tournamentId: TournamentId,
    clubId: ClubId
) extends APIMessage[Unit]:

  override def plan(context: ApiPlanContext): IO[Unit] =
    for
      tournament <- IO.blocking(TournamentTable.findById(context.connection, tournamentId))
      _ <- IO.blocking {
        tournament.foreach { tournament =>
          ensureClubInvitedOrParticipating(tournament)
          TournamentTable.save(context.connection, TournamentFunctions.registerClub(tournament, clubId))
        }
      }
    yield ()

  private def ensureClubInvitedOrParticipating(tournament: Tournament): Unit =
    val alreadyParticipating = tournament.participatingClubs.contains(clubId)
    val isWhitelisted = tournament.whitelist.exists(_.clubId.contains(clubId))
    if !alreadyParticipating && !isWhitelisted then
      throw IllegalArgumentException(
        s"Club ${clubId.value} is not invited to tournament ${tournamentId.value}"
      )
