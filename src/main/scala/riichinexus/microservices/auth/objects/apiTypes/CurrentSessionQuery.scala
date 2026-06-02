package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionQuery(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
)

object CurrentSessionQuery:
  given ReadWriter[CurrentSessionQuery] = macroRW
