package riichinexus.microservices.opsanalytics.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object DomainEventResponseCodecs:
  given ReadWriter[DomainEventOutboxHistoryView] = macroRW
