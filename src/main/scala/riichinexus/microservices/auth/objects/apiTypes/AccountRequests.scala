package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.*

final case class RegisterAccountRequest(
    username: String,
    password: String,
    displayName: String
)

object RegisterAccountRequest:
  given ReadWriter[RegisterAccountRequest] = macroRW

final case class LoginRequest(
    username: String,
    password: String
)

object LoginRequest:
  given ReadWriter[LoginRequest] = macroRW
