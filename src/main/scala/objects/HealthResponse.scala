package objects

import java.time.Instant

import api.contracts.JsonSupport.given
import upickle.default.*

final case class HealthResponse(
    status: String,
    storage: String,
    timestamp: Instant,
    service: String = "riichi-nexus"
)

object HealthResponse:
  given ReadWriter[HealthResponse] = macroRW
