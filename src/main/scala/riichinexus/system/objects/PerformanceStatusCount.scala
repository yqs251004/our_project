package riichinexus.system.objects

import upickle.default.*

final case class PerformanceStatusCount(
    statusCode: Int,
    count: Long
) derives CanEqual

object PerformanceStatusCount:
  given ReadWriter[PerformanceStatusCount] = macroRW
