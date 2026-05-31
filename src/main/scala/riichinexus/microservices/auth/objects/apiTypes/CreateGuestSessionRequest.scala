package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.*

final case class CreateGuestSessionRequest(
    displayName: Option[String] = None,
    ttlHours: Option[Int] = None,
    deviceFingerprint: Option[String] = None
)

object CreateGuestSessionRequest:
  given ReadWriter[CreateGuestSessionRequest] = macroRW
