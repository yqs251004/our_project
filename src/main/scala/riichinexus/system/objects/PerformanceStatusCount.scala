package riichinexus.system.objects

import upickle.default.*

final case class PerformanceStatusCount(
    statusCode: Int,
    count: Long
)

object PerformanceStatusCount:
  given ReadWriter[PerformanceStatusCount] = macroRW
