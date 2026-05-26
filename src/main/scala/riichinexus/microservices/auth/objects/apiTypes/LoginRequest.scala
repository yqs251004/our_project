package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.*

final case class LoginRequest(
    username: String,
    password: String
)

object LoginRequest:
  given ReadWriter[LoginRequest] = macroRW
