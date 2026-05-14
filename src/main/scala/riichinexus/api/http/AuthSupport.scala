package riichinexus.api.http

import java.util.NoSuchElementException

import org.http4s.Request
import cats.effect.IO
import riichinexus.domain.model.*
import riichinexus.microservices.auth.api.responses.*

trait AuthSupport:
  protected def routeContext: RouteContext

  private def authModule = routeContext.authModule

  def principal(playerId: PlayerId): AccessPrincipal =
    authModule.tables
      .findPlayer(playerId)
      .map(_.asPrincipal)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

  def queryPrincipal(request: Request[IO]): AccessPrincipal =
    val operatorId = queryParam(request, "operatorId")
      .map(PlayerId(_))
      .getOrElse(throw IllegalArgumentException("Query parameter operatorId is required"))
    principal(operatorId)

  def guestPrincipal(sessionId: GuestSessionId): AccessPrincipal =
    authModule.guestSessionService
      .touchActiveSession(sessionId)
      .map(AccessPrincipal.guest)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))

  def requestActor(guestSessionId: Option[GuestSessionId], operatorId: Option[PlayerId]): AccessPrincipal =
    if guestSessionId.nonEmpty && operatorId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    guestSessionId.map(guestPrincipal)
      .orElse(operatorId.map(principal))
      .getOrElse(AccessPrincipal.guest())

  def requirePermission(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  ): Unit =
    routeContext.authorizationService.requirePermission(
      principal = principal,
      permission = permission,
      clubId = clubId,
      tournamentId = tournamentId,
      subjectPlayerId = subjectPlayerId
    )

  def resolveCurrentSessionView(
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  ): CurrentSessionView =
    if operatorId.nonEmpty && guestSessionId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    operatorId.map(playerId =>
      authModule.tables.findPlayer(playerId)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    ) match
      case Some(player) =>
        CurrentSessionView(
          principalKind = SessionPrincipalKind.RegisteredPlayer,
          principalId = player.id.value,
          displayName = player.nickname,
          authenticated = true,
          roles = registeredRoleFlags(player),
          player = Some(player)
        )
      case None =>
        guestSessionId.map(sessionId =>
          authModule.guestSessionService.touchActiveSession(sessionId)
            .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
        ) match
          case Some(session) =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Guest,
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
              guestSession = Some(session)
            )
          case None =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Anonymous,
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
      isClubAdmin = player.roleGrants.exists(_.role == RoleKind.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == RoleKind.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin)
    )

  protected def queryParam(request: Request[IO], key: String): Option[String]
