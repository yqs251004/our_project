package riichinexus.microservices.auth.api.session
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.auth.objects.session.GuestSessionId
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.auth.objects.session.SessionPrincipalKind
import riichinexus.microservices.auth.objects.session.CurrentSessionGuestSessionView
import riichinexus.microservices.auth.objects.session.CurrentSessionPlayerView
import riichinexus.microservices.auth.objects.session.CurrentSessionRoleFlags
import riichinexus.microservices.auth.objects.session.CurrentSessionView
import riichinexus.microservices.auth.api.session.`private`.ResolveGuestSessionAuthPrivateAPIMessage
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.json.JsonCodecs.given
/** 获取当前访问会话信息。 */
final case class CurrentSessionAuthAPIMessage(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[CurrentSessionView]:

  override def plan(context: ApiPlanContext): IO[CurrentSessionView] =
    for
      resolvedOperatorId <- IO.blocking(operatorId.filter(_.nonEmpty).map(PlayerId(_)))
      resolvedGuestSessionId <- IO.blocking(guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)))
      session <- resolveCurrentSession(context, resolvedOperatorId, resolvedGuestSessionId)
    yield session

  private def resolveCurrentSession(
      context: ApiPlanContext,
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  ): IO[CurrentSessionView] =
    if operatorId.nonEmpty && guestSessionId.nonEmpty then
      IO.raiseError(IllegalArgumentException("guestSessionId and operatorId cannot be provided together"))
    else
      operatorId match
        case Some(playerId) =>
          ResolvePlayerPrivateAPIMessage(playerId)
            .plan(context)
            .map(
              _.map(registeredPlayerView)
                .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
          )
        case None =>
          guestSessionId match
            case Some(sessionId) =>
              ResolveGuestSessionAuthPrivateAPIMessage(sessionId).plan(context)
                .map(guestSessionView)
            case None =>
              IO.pure(anonymousView)

  private def registeredRoleFlags(player: PlayerPrivateView): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  private def registeredPlayerView(player: PlayerPrivateView): CurrentSessionView =
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

  private def guestSessionView(session: riichinexus.microservices.auth.domain.session.model.GuestAccessSession): CurrentSessionView =
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

  private def anonymousView: CurrentSessionView =
    CurrentSessionView(
      principalKind = SessionPrincipalKind.Anonymous,
      principalId = "anonymous",
      displayName = "Guest",
      authenticated = false,
      roles = guestRoleFlags
    )

  private def guestRoleFlags: CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = true,
      isRegisteredPlayer = false,
      isClubAdmin = false,
      isTournamentAdmin = false,
      isSuperAdmin = false
    )
