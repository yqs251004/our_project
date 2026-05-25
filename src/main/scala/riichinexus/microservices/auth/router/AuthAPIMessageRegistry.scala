package riichinexus.microservices.auth.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.api.*
import riichinexus.microservices.auth.objects.apiTypes.*
import riichinexus.microservices.auth.objects.apiTypes.AuthResponses.given
import riichinexus.system.objects.PagedResponse

object AuthAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.created[RegisterAuthAPIMessage, AuthSuccessResponse],
      RegisteredAPIMessage.api[LoginAuthAPIMessage, AuthSuccessResponse],
      RegisteredAPIMessage.apiWithToken[RestoreAuthSessionAPIMessage, AuthSessionResponse],
      RegisteredAPIMessage.apiWithToken[LogoutAuthAPIMessage, ApiMessage],
      RegisteredAPIMessage.api[CurrentSessionAuthAPIMessage, CurrentSessionResponse],
      RegisteredAPIMessage.api[AuthCheckPermissionAPIMessage, Boolean],
      RegisteredAPIMessage.api[ListGuestSessionsAuthAPIMessage, PagedResponse[GuestSessionResponse]],
      RegisteredAPIMessage.created[CreateGuestSessionAuthAPIMessage, GuestSessionResponse],
      RegisteredAPIMessage.api[GetGuestSessionAuthAPIMessage, GuestSessionResponse],
      RegisteredAPIMessage.api[RevokeGuestSessionAuthAPIMessage, GuestSessionResponse],
      RegisteredAPIMessage.api[UpgradeGuestSessionAuthAPIMessage, GuestSessionResponse]
    )
