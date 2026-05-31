package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.{AuthSuccessView, CurrentSessionRoleFlags}
import upickle.default.*

final case class AuthSuccessResponse(
    userId: String,
    username: String,
    displayName: String,
    token: String,
    roles: CurrentSessionRoleFlags
)

object AuthSuccessResponse:
  given ReadWriter[AuthSuccessResponse] = macroRW

  def fromView(view: AuthSuccessView): AuthSuccessResponse =
    AuthSuccessResponse(
      userId = view.userId,
      username = view.username,
      displayName = view.displayName,
      token = view.token,
      roles = view.roles
    )
