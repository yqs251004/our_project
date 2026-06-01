package riichinexus.microservices.auth.router
import riichinexus.api.functions.RegisteredAPIMessageFunctions

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.api.*
import riichinexus.microservices.auth.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse

object AuthAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessageFunctions.created[RegisterAuthAPIMessage, AuthSuccessView],
      RegisteredAPIMessageFunctions.api[LoginAuthAPIMessage, AuthSuccessView],
      RegisteredAPIMessageFunctions.apiWithToken[RestoreAuthSessionAPIMessage, AuthSessionView],
      RegisteredAPIMessageFunctions.apiWithToken[LogoutAuthAPIMessage, LogoutResponse],
      RegisteredAPIMessageFunctions.api[CurrentSessionAuthAPIMessage, CurrentSessionView],
      RegisteredAPIMessageFunctions.api[AuthCheckPermissionAPIMessage, Boolean],
      RegisteredAPIMessageFunctions.api[ListGuestSessionsAuthAPIMessage, PagedResponse[GuestSessionResponse]],
      RegisteredAPIMessageFunctions.created[CreateGuestSessionAuthAPIMessage, GuestSessionResponse],
      RegisteredAPIMessageFunctions.api[GetGuestSessionAuthAPIMessage, GuestSessionResponse],
      RegisteredAPIMessageFunctions.api[RevokeGuestSessionAuthAPIMessage, GuestSessionResponse],
      RegisteredAPIMessageFunctions.api[UpgradeGuestSessionAuthAPIMessage, GuestSessionResponse]
    )
