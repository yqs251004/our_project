package riichinexus.api.runtime

import java.sql.Connection
import java.util.NoSuchElementException

import cats.effect.unsafe.implicits.global
import scala.util.Try

import riichinexus.bootstrap.*
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.auth.domain.AuthorizationPolicy
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.SessionPrincipalKind
import riichinexus.microservices.auth.objects.apiTypes.{
  CurrentSessionGuestSessionView,
  CurrentSessionPlayerView,
  CurrentSessionRoleFlags,
  CurrentSessionView
}
import riichinexus.api.ApiPlanContext
import riichinexus.microservices.auth.api.`private`.ResolveGuestSessionAuthPrivateAPIMessage
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.player.objects.Player

final class ApiPlanSupport(
    val executionContext: ApiExecutionContext
):
  val authModule: AuthModuleContext = executionContext.authModule
  val playerModule: PlayerModuleContext = executionContext.playerModule
  val clubModule: ClubModuleContext = executionContext.clubModule
  val opsAnalyticsModule: OpsAnalyticsModuleContext = executionContext.opsAnalyticsModule
  val tournamentModule: TournamentModuleContext = executionContext.tournamentModule
  val platformAdminModule: PlatformAdminModuleContext = executionContext.platformAdminModule
  val tournamentAppealModule: TournamentAppealModuleContext = executionContext.tournamentAppealModule
  val authorizationService: AuthorizationPolicy = executionContext.authorizationService
  val storageLabel: String = executionContext.storageLabel

  def principal(connection: Connection, playerId: PlayerId): AccessPrincipal =
    findPlayer(connection, playerId)
      .map(_.asPrincipal)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

  def guestPrincipal(connection: Connection, sessionId: GuestSessionId): AccessPrincipal =
    AccessPrincipal.guest(touchGuestSession(connection, sessionId))

  def requestActor(connection: Connection, guestSessionId: Option[GuestSessionId], operatorId: Option[PlayerId]): AccessPrincipal =
    if guestSessionId.nonEmpty && operatorId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    guestSessionId.map(guestPrincipal(connection, _))
      .orElse(operatorId.map(principal(connection, _)))
      .getOrElse(AccessPrincipal.guest())

  def requirePermission(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  ): Unit =
    authorizationService.requirePermission(
      principal = principal,
      permission = permission,
      clubId = clubId,
      tournamentId = tournamentId,
      subjectPlayerId = subjectPlayerId
    )

  def resolveCurrentSessionView(
      connection: Connection,
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  ): CurrentSessionView =
    if operatorId.nonEmpty && guestSessionId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    operatorId.map(playerId =>
      findPlayer(connection, playerId)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    ) match
      case Some(player) =>
        CurrentSessionView(
          principalKind = SessionPrincipalKind.RegisteredPlayer.toString,
          principalId = player.id.value,
          displayName = player.nickname,
          authenticated = true,
          roles = registeredRoleFlags(player),
          player = Some(
            CurrentSessionPlayerView(
              id = player.id.value,
              userId = player.userId,
              nickname = player.nickname
            )
          )
        )
      case None =>
        guestSessionId.map(sessionId =>
          touchGuestSession(connection, sessionId)
        ) match
          case Some(session) =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Guest.toString,
              principalId = session.id.value,
              displayName = session.displayName,
              authenticated = true,
              roles = CurrentSessionRoleFlags(
                isGuest = true,
                isRegisteredPlayer = false,
                isClubAdmin = false,
                isTournamentAdmin = false,
                isSuperAdmin = false
              ),
              guestSession = Some(
                CurrentSessionGuestSessionView(
                  id = session.id.value,
                  displayName = session.displayName
                )
              )
            )
          case None =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Anonymous.toString,
              principalId = "anonymous",
              displayName = "Guest",
              authenticated = false,
              roles = CurrentSessionRoleFlags(
                isGuest = true,
                isRegisteredPlayer = false,
                isClubAdmin = false,
                isTournamentAdmin = false,
                isSuperAdmin = false
              )
            )

  def registeredRoleFlags(player: Player): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  def parseEnum[E](label: String, value: String)(parse: String => E): E =
    Try(parse(value)).getOrElse(throw IllegalArgumentException(s"Invalid $label: $value"))

  def containsIgnoreCase(value: String, fragment: String): Boolean =
    value.toLowerCase.contains(fragment.toLowerCase)

  private def touchGuestSession(connection: Connection, sessionId: GuestSessionId): GuestAccessSession =
    ResolveGuestSessionAuthPrivateAPIMessage(sessionId)
      .plan(ApiPlanContext(this, bearerToken = None, connection = connection))
      .unsafeRunSync()

  private def findPlayer(connection: Connection, playerId: PlayerId): Option[Player] =
    GetPlayerAPIMessage.findPlayer(connection, playerId)

object ApiPlanSupport:

  def apply(executionContext: ApiExecutionContext): ApiPlanSupport =
    new ApiPlanSupport(executionContext)
