package riichinexus.microservices.club.api.membership
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerClubAdminRevocationPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.profile.ClubView
/** 撤销玩家俱乐部管理员身份。 */
final case class RevokeClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- resolveOperatorActor(context)
      requestedClubId = ClubId(clubId)
      requestedPlayerId = PlayerId(playerId)
      club <- revokeAdmin(context, requestedClubId, requestedPlayerId, actor).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield ClubViewFunctions.clubView(club)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty)
      .map(id => ResolveAccessPrincipalPrivateAPIMessage(PlayerId(id)).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def revokeAdmin(
      context: ApiPlanContext,
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId))
      _ <- ResolvePlayerPrivateAPIMessage(playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureAdminCanBeRevoked(club, clubId, playerId, actor)
          for
            _ <- RecordPlayerClubAdminRevocationPrivateAPIMessage(playerId, clubId).plan(context)
            savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.revokeAdmin(club, playerId)))
          yield Some(savedClub)
    yield savedClub

  private def ensureAdminCanBeRevoked(
      club: Club,
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ClubAuthorization.requireClubMember(club, playerId, "revoke club admin")
    ClubAuthorization.requireClubAdmin(actor = actor,
      club = club,
      permission = Permission.AssignClubAdmin
    )
    ensureTargetIsAdmin(club, clubId, playerId)
    ensureAnotherAdminRemains(club, clubId)

  private def ensureTargetIsAdmin(club: Club, clubId: ClubId, playerId: PlayerId): Unit =
    if !club.admins.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} is not a club admin of club ${clubId.value}"
      )

  private def ensureAnotherAdminRemains(club: Club, clubId: ClubId): Unit =
    if club.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Club ${clubId.value} must retain at least one club admin"
      )
