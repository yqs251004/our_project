package riichinexus.microservices.club.objects.apiTypes

import upickle.default.*

final case class ClubApplicationDetailQuery(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
)

object ClubApplicationDetailQuery:
  given ReadWriter[ClubApplicationDetailQuery] = macroRW
