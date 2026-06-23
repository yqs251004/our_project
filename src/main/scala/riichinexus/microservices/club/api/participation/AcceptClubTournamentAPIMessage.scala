package riichinexus.microservices.club.api.participation
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.api.competition.TournamentGetAPIMessage
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.tournament.api.competition.`private`.RecordClubTournamentAcceptancePrivateAPIMessage
import riichinexus.microservices.tournament.objects.competition.TournamentMutationView
/** 俱乐部接受赛事邀请。 */
final case class AcceptClubTournamentAPIMessage(
    clubId: String,
    tournamentId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView]:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- resolveOperatorActor(context)
      requestedClubId = ClubId(clubId)
      requestedTournamentId = TournamentId(tournamentId)
      _ <- acceptTournament(context, requestedClubId, requestedTournamentId, actor)
      detail <- TournamentGetAPIMessage(requestedTournamentId.value).plan(context)
    yield TournamentMutationView(tournament = detail, scheduledTables = Vector.empty)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty)
      .map(id => ResolveAccessPrincipalPrivateAPIMessage(PlayerId(id)).plan(context))
      .getOrElse(throw IllegalArgumentException("operatorId is required"))

  private def acceptTournament(
      context: ApiPlanContext,
      clubId: ClubId,
      tournamentId: TournamentId,
      actor: AccessPrincipalPrivateView
  ): IO[Unit] =
    for
      club <- IO.blocking(resolveActiveClub(context.connection, clubId))
      _ <- IO.blocking(requireClubLineupCapability(actor, club))
      _ <- RecordClubTournamentAcceptancePrivateAPIMessage(tournamentId, clubId).plan(context)
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
