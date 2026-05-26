package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.*

final case class ListGuestSessionsRequest(
    activeOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ListGuestSessionsRequest:
  given ReadWriter[ListGuestSessionsRequest] = macroRW
