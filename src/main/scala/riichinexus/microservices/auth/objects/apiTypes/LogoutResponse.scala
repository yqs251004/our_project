package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.*

final case class LogoutResponse(
    message: String
)

object LogoutResponse:
  given ReadWriter[LogoutResponse] = macroRW
