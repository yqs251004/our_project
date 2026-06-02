package riichinexus.microservices.auth.router
import riichinexus.system.api.RegisteredAPIMessage

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.api.*
import riichinexus.microservices.auth.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse

object AuthAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.created[RegisterAuthAPIMessage, AuthSuccessView],
      RegisteredAPIMessage.api[LoginAuthAPIMessage, AuthSuccessView],
      RegisteredAPIMessage.apiWithToken[RestoreAuthSessionAPIMessage, AuthSessionView],
      RegisteredAPIMessage.apiWithToken[LogoutAuthAPIMessage, LogoutResponse],
      RegisteredAPIMessage.api[CurrentSessionAuthAPIMessage, CurrentSessionView],
      RegisteredAPIMessage.api[AuthCheckPermissionAPIMessage, Boolean],
      RegisteredAPIMessage.api[ListGuestSessionsAuthAPIMessage, PagedResponse[GuestSessionResponse]],
      RegisteredAPIMessage.created[CreateGuestSessionAuthAPIMessage, GuestSessionResponse],
      RegisteredAPIMessage.api[GetGuestSessionAuthAPIMessage, GuestSessionResponse],
      RegisteredAPIMessage.api[RevokeGuestSessionAuthAPIMessage, GuestSessionResponse],
      RegisteredAPIMessage.api[UpgradeGuestSessionAuthAPIMessage, GuestSessionResponse]
    )
