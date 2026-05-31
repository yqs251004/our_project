package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.*

final case class GuestSessionResponse(
    id: String,
    displayName: String,
    createdAt: String
)

object GuestSessionResponse:
  given ReadWriter[GuestSessionResponse] = macroRW
