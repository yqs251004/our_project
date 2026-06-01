package riichinexus.microservices.auth.api.`private`

import java.util.NoSuchElementException

import riichinexus.api.ApiPlanContext
import riichinexus.domain.model.{GuestSessionId, PlayerId}
import riichinexus.microservices.auth.domain.model.Role
import riichinexus.microservices.auth.objects.SessionPrincipalKind
import riichinexus.microservices.auth.objects.apiTypes.{
  CurrentSessionGuestSessionView,
  CurrentSessionPlayerView,
  CurrentSessionRoleFlags,
  CurrentSessionView
}
import riichinexus.microservices.player.api.GetPlayerAPIMessage
import riichinexus.microservices.player.domain.Player

object CurrentSessionViewResolver:

  def resolve(
      context: ApiPlanContext,
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  ): CurrentSessionView =
    if operatorId.nonEmpty && guestSessionId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    operatorId.map(playerId =>
      GetPlayerAPIMessage.findPlayer(context.connection, playerId)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    ) match
      case Some(player) => registeredPlayerView(player)
      case None =>
        guestSessionId.map(AuthAccessPrincipalResolver.resolveGuestSession(context, _)) match
          case Some(session) =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Guest,
              principalId = session.id.value,
              displayName = session.displayName,
              authenticated = true,
              roles = guestRoleFlags,
              guestSession = Some(
                CurrentSessionGuestSessionView(
                  id = session.id.value,
                  displayName = session.displayName
                )
              )
            )
          case None =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Anonymous,
              principalId = "anonymous",
              displayName = "Guest",
              authenticated = false,
              roles = guestRoleFlags
            )

  def registeredRoleFlags(player: Player): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  private def registeredPlayerView(player: Player): CurrentSessionView =
    CurrentSessionView(
      principalKind = SessionPrincipalKind.RegisteredPlayer,
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

  private def guestRoleFlags: CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = true,
      isRegisteredPlayer = false,
      isClubAdmin = false,
      isTournamentAdmin = false,
      isSuperAdmin = false
    )
