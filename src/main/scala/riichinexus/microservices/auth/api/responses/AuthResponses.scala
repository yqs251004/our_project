package riichinexus.microservices.auth.api.responses

import riichinexus.domain.model.{GuestAccessSession, Player, PlayerId}

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
  export AuthResponseCodecs.given

object AuthResponses:
  export AuthResponseCodecs.given
