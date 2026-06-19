package riichinexus.microservices.club.api
import riichinexus.microservices.club.domain.functions.ClubViewFunctions
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{RecordPlayerClubAdminRevocationPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
/** 撤销玩家俱乐部管理员身份。 */
final case class RevokeClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- resolveOperatorActor(context)
      command = RevokeClubAdminCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor
      )
      club <- revokeAdmin(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield ClubViewFunctions.clubView(club)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty)
      .map(id => ResolveAccessPrincipalPrivateAPIMessage(PlayerId(id)).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def revokeAdmin(
      context: ApiPlanContext,
      command: RevokeClubAdminCommand
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId))
      _ <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureAdminCanBeRevoked(club, command)
          for
            _ <- RecordPlayerClubAdminRevocationPrivateAPIMessage(command.playerId, command.clubId).plan(context)
            savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.revokeAdmin(club, command.playerId)))
          yield Some(savedClub)
    yield savedClub

  private def ensureAdminCanBeRevoked(
      club: Club,
      command: RevokeClubAdminCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ClubAuthorization.requireClubMember(club, command.playerId, "revoke club admin")
    ClubAuthorization.requireClubAdmin(actor = command.actor,
      club = club,
      permission = Permission.AssignClubAdmin
    )
    ensureTargetIsAdmin(club, command)
    ensureAnotherAdminRemains(club, command)

  private def ensureTargetIsAdmin(club: Club, command: RevokeClubAdminCommand): Unit =
    if !club.admins.contains(command.playerId) then
      throw IllegalArgumentException(
        s"Player ${command.playerId.value} is not a club admin of club ${command.clubId.value}"
      )

  private def ensureAnotherAdminRemains(club: Club, command: RevokeClubAdminCommand): Unit =
    if club.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Club ${command.clubId.value} must retain at least one club admin"
      )

  private final case class RevokeClubAdminCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  )
