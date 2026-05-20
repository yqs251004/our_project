package riichinexus.microservices.auth.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.domain.model.GuestAccessSession
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
      RegisteredAPIMessage.api[ListGuestSessionsAuthAPIMessage, PagedResponse[GuestAccessSession]],
      RegisteredAPIMessage.created[CreateGuestSessionAuthAPIMessage, GuestAccessSession],
      RegisteredAPIMessage.api[GetGuestSessionAuthAPIMessage, GuestAccessSession],
      RegisteredAPIMessage.api[RevokeGuestSessionAuthAPIMessage, GuestAccessSession],
      RegisteredAPIMessage.api[UpgradeGuestSessionAuthAPIMessage, GuestAccessSession]
    )
