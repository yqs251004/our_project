package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.domain.model.{GuestAccessSession, Player, PlayerId}
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

final case class CurrentSessionView(
    principalKind: SessionPrincipalKind,
    principalId: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags,
    player: Option[Player] = None,
    guestSession: Option[GuestAccessSession] = None
) derives CanEqual

final case class AuthSuccessView(
    userId: PlayerId,
    username: String,
    displayName: String,
    token: String,
    roles: CurrentSessionRoleFlags
) derives CanEqual

final case class AuthSessionView(
    userId: PlayerId,
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
  given ReadWriter[SessionPrincipalKind] =
    readwriter[String].bimap[SessionPrincipalKind](
      _.toString,
      SessionPrincipalKind.valueOf
    )
  given ReadWriter[CurrentSessionRoleFlags] = macroRW
  given ReadWriter[CurrentSessionView] = macroRW
  given ReadWriter[AuthSuccessView] = macroRW
  given ReadWriter[AuthSessionView] = macroRW
