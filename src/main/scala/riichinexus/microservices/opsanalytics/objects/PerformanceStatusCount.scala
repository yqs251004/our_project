package riichinexus.microservices.opsanalytics.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class PerformanceStatusCount(
    statusCode: Int,
    count: Long
) derives CanEqual

object PerformanceStatusCount:
  given ReadWriter[PerformanceStatusCount] = macroRW
