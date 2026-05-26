package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.*

final case class RevokeGuestSessionRequest(
    reason: Option[String] = None
)

object RevokeGuestSessionRequest:
  given ReadWriter[RevokeGuestSessionRequest] = macroRW
