package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.api.TournamentGetAPIMessage
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.tournament.api.`private`.RecordClubTournamentDeclinePrivateAPIMessage
import riichinexus.microservices.tournament.objects.competition.apiTypes.TournamentMutationView
/** 俱乐部拒绝赛事邀请。 */
final case class DeclineClubTournamentAPIMessage(
    clubId: String,
    tournamentId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView]:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      command = DeclineClubTournamentCommand(
        clubId = ClubId(clubId),
        tournamentId = TournamentId(tournamentId),
        actor = actor
      )
      _ <- declineTournament(context, command)
      detail <- TournamentGetAPIMessage(command.tournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty)
      .map(id => ResolveAccessPrincipalPrivateAPIMessage(PlayerId(id)).plan(context))
      .getOrElse(throw IllegalArgumentException("operatorId is required"))

  private def declineTournament(
      context: ApiPlanContext,
      command: DeclineClubTournamentCommand
  ): IO[Unit] =
    for
      club <- IO.blocking(resolveActiveClub(context.connection, command.clubId))
      _ <- IO.blocking(requireClubLineupCapability(command.actor, club))
      _ <- RecordClubTournamentDeclinePrivateAPIMessage(command.tournamentId, command.clubId).plan(context)
    yield ()

  private def resolveActiveClub(connection: java.sql.Connection, clubId: ClubId): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable
      .findById(connection, clubId)
      .map { club =>
        ClubAuthorization.ensureClubActive(club)
        club
      }
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

  private def requireClubLineupCapability(
      actor: AccessPrincipalPrivateView,
      club: Club
  ): Unit =
    ClubAuthorization.requireClubCapability(
      actor = actor,
      club = club,
      permission = Permission.SubmitTournamentLineup,
      delegatedPrivileges = Set(ClubPrivilegeCode.PriorityLineup)
    )

  private final case class DeclineClubTournamentCommand(
      clubId: ClubId,
      tournamentId: TournamentId,
      actor: AccessPrincipalPrivateView
  )

