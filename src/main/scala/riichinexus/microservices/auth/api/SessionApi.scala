package riichinexus.microservices.auth.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.auth.api.responses.*
import riichinexus.microservices.auth.objects.CurrentSessionQuery
import riichinexus.microservices.auth.tables.AuthTables

object SessionApi:

  def restoreSession(
      service: AuthApplicationService,
      token: String,
      asOf: Instant = Instant.now()
  ): AuthSessionView =
    service.restoreSession(token, asOf)

  def logout(
      service: AuthApplicationService,
      token: String,
      loggedOutAt: Instant = Instant.now()
  ): ApiMessage =
    service.logout(token, loggedOutAt)
    ApiMessage("Logged out")

  def currentSessionView(
      tables: AuthTables,
      guestSessionService: GuestSessionApplicationService,
      query: CurrentSessionQuery
  ): CurrentSessionView =
    if query.operatorId.nonEmpty && query.guestSessionId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    query.operatorId.map(playerId =>
      tables.findPlayer(playerId)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    ) match
      case Some(player) =>
        CurrentSessionView(
          principalKind = SessionPrincipalKind.RegisteredPlayer,
          principalId = player.id.value,
          displayName = player.nickname,
          authenticated = true,
          roles = CurrentSessionRoleFlags(
            isGuest = false,
            isRegisteredPlayer = true,
            isClubAdmin = player.roleGrants.exists(_.role == RoleKind.ClubAdmin),
            isTournamentAdmin = player.roleGrants.exists(_.role == RoleKind.TournamentAdmin),
            isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin)
          ),
          player = Some(player)
        )
      case None =>
        query.guestSessionId.map(sessionId =>
          guestSessionService.touchActiveSession(sessionId)
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
