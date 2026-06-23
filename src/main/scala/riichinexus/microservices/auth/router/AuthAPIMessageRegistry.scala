package riichinexus.microservices.auth.router
import riichinexus.system.api.RegisteredAPIMessage


import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.api.authorization.AuthCheckPermissionAPIMessage
import riichinexus.microservices.auth.api.account.BootstrapSuperAdminAuthAPIMessage
import riichinexus.microservices.auth.api.session.CreateGuestSessionAuthAPIMessage
import riichinexus.microservices.auth.api.session.CurrentSessionAuthAPIMessage
import riichinexus.microservices.auth.api.session.GetGuestSessionAuthAPIMessage
import riichinexus.microservices.auth.api.session.ListGuestSessionsAuthAPIMessage
import riichinexus.microservices.auth.api.account.LoginAuthAPIMessage
import riichinexus.microservices.auth.api.session.LogoutAuthAPIMessage
import riichinexus.microservices.auth.api.account.RegisterAuthAPIMessage
import riichinexus.microservices.auth.api.session.RestoreAuthSessionAPIMessage
import riichinexus.microservices.auth.api.session.RevokeGuestSessionAuthAPIMessage
import riichinexus.microservices.auth.api.session.UpgradeGuestSessionAuthAPIMessage
import riichinexus.microservices.auth.objects.session.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.objects.session.apiTypes.LogoutResponse
import riichinexus.microservices.auth.objects.session.AuthSessionView
import riichinexus.microservices.auth.objects.session.AuthSuccessView
import riichinexus.microservices.auth.objects.session.CurrentSessionView
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
