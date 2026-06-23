package riichinexus.microservices.club.api.membership
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerClubAdminGrantPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus

import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.profile.ClubView
/** 授予玩家俱乐部管理员身份。 */
final case class AssignClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      grantedAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      requestedPlayerId = PlayerId(playerId)
      club <- assignAdmin(context, requestedClubId, requestedPlayerId, actor, grantedAt).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield ClubViewFunctions.clubView(club)

  private def assignAdmin(
      context: ApiPlanContext,
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      grantedAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId))
      player <- ResolvePlayerPrivateAPIMessage(playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureAdminCanBeAssigned(club, player, clubId, playerId, actor)
          for
            _ <- RecordPlayerClubAdminGrantPrivateAPIMessage(
              playerId,
              clubId,
              grantedAt,
              actor.playerId
            ).plan(context)
            savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.grantAdmin(club, playerId)))
          yield Some(savedClub)
    yield savedClub

  private def ensureAdminCanBeAssigned(
      club: Club,
      player: PlayerPrivateView,
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"PlayerPrivateView ${playerId.value} cannot be granted club admin")
    ClubAuthorization.requireClubMember(club, playerId, "assign club admin")
    ClubAuthorization.requireClubAdmin(actor = actor,
      club = club,
      permission = Permission.AssignClubAdmin
    )

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)
