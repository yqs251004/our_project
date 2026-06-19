package riichinexus.microservices.auth.router
import riichinexus.system.api.RegisteredAPIMessage


import riichinexus.microservices.auth.api.{AuthCheckPermissionAPIMessage, BootstrapSuperAdminAuthAPIMessage, CreateGuestSessionAuthAPIMessage, CurrentSessionAuthAPIMessage, GetGuestSessionAuthAPIMessage, ListGuestSessionsAuthAPIMessage, LoginAuthAPIMessage, LogoutAuthAPIMessage, RegisterAuthAPIMessage, RestoreAuthSessionAPIMessage, RevokeGuestSessionAuthAPIMessage, UpgradeGuestSessionAuthAPIMessage}
import riichinexus.microservices.auth.objects.apiTypes.{AuthSessionView, AuthSuccessView, CurrentSessionView, GuestSessionResponse, LogoutResponse}
import riichinexus.system.objects.PagedResponse


object AuthAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.created[BootstrapSuperAdminAuthAPIMessage, AuthSuccessView],
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
