package riichinexus.microservices.auth.api
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.SessionPrincipalKind
import riichinexus.microservices.auth.objects.apiTypes.{CurrentSessionGuestSessionView, CurrentSessionPlayerView, CurrentSessionRoleFlags, CurrentSessionView}
import riichinexus.microservices.auth.api.`private`.ResolveGuestSessionAuthPrivateAPIMessage
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 获取当前访问会话信息。 */
final case class CurrentSessionAuthAPIMessage(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[CurrentSessionView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[CurrentSessionView] =
    for
      input <- IO.blocking(resolveInput)
      session <- resolveCurrentSession(context, input)
    yield session

  private def resolveInput: CurrentSessionInput =
    CurrentSessionInput(
      operatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_)),
      guestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
    )

  private def resolveCurrentSession(
      context: ApiPlanContext,
      input: CurrentSessionInput
  ): IO[CurrentSessionView] =
    if input.operatorId.nonEmpty && input.guestSessionId.nonEmpty then
      IO.raiseError(IllegalArgumentException("guestSessionId and operatorId cannot be provided together"))
    else
      input.operatorId match
        case Some(playerId) =>
          ResolvePlayerPrivateAPIMessage(playerId)
            .plan(context)
            .map(
              _.map(registeredPlayerView)
                .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
          )
        case None =>
          input.guestSessionId match
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

  private def guestSessionView(session: riichinexus.microservices.auth.domain.model.GuestAccessSession): CurrentSessionView =
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

  private final case class CurrentSessionInput(
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  )
