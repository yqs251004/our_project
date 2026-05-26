package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.*

final case class ApiMessage(
    message: String
)

object ApiMessage:
  given ReadWriter[ApiMessage] = macroRW
