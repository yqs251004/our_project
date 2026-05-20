package riichinexus.system.objects.apiTypes

import java.time.Instant

final case class HealthResponse(
    status: String,
    storage: String,
    timestamp: Instant,
    service: String = "riichi-nexus"
)

object HealthResponse:
  export SharedResponseCodecs.given
