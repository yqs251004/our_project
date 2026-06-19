package riichinexus.system.objects

import upickle.default.{ReadWriter, macroRW}

final case class PerformanceStatusCount(
    statusCode: Int,
    count: Long
)

object PerformanceStatusCount:
  given ReadWriter[PerformanceStatusCount] = macroRW
