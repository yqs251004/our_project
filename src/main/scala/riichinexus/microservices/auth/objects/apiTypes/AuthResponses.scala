package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.domain.model.{GuestAccessSession, Player}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

enum SessionPrincipalKind derives CanEqual:
  case Anonymous
  case Guest
  case RegisteredPlayer

final case class CurrentSessionRoleFlags(
    isGuest: Boolean,
    isRegisteredPlayer: Boolean,
    isClubAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isSuperAdmin: Boolean
) derives CanEqual

final case class CurrentSessionPlayerView(
    id: String,
    userId: String,
    nickname: String
) derives CanEqual

object CurrentSessionPlayerView:
  def fromDomain(player: Player): CurrentSessionPlayerView =
    CurrentSessionPlayerView(
      id = player.id.value,
      userId = player.userId,
      nickname = player.nickname
    )

final case class CurrentSessionGuestSessionView(
    id: String,
    displayName: String
) derives CanEqual

object CurrentSessionGuestSessionView:
  def fromDomain(session: GuestAccessSession): CurrentSessionGuestSessionView =
    CurrentSessionGuestSessionView(
      id = session.id.value,
      displayName = session.displayName
    )

final case class CurrentSessionView(
    principalKind: String,
    principalId: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags,
    player: Option[CurrentSessionPlayerView] = None,
    guestSession: Option[CurrentSessionGuestSessionView] = None
) derives CanEqual

final case class AuthSuccessView(
    userId: String,
    username: String,
    displayName: String,
    token: String,
    roles: CurrentSessionRoleFlags
) derives CanEqual

final case class AuthSessionView(
    userId: String,
    username: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags
) derives CanEqual

type AuthSuccessResponse = AuthSuccessView
type AuthSessionResponse = AuthSessionView
type CurrentSessionResponse = CurrentSessionView

final case class ApiMessage(
    message: String
)

object ApiMessage:
  export AuthResponses.given

object AuthResponses:
  given ReadWriter[ApiMessage] = macroRW
  given ReadWriter[CurrentSessionRoleFlags] = macroRW
  given ReadWriter[CurrentSessionPlayerView] = macroRW
  given ReadWriter[CurrentSessionGuestSessionView] = macroRW
  given ReadWriter[CurrentSessionView] = macroRW
  given ReadWriter[AuthSuccessView] = macroRW
  given ReadWriter[AuthSessionView] = macroRW
