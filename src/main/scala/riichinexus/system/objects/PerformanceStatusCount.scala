package riichinexus.system.objects

import upickle.default.{ReadWriter, macroRW}

/** 某个 HTTP 状态码在性能指标中的出现次数。 */
final case class PerformanceStatusCount(
    statusCode: Int,
    count: Long
)

object PerformanceStatusCount:
  given ReadWriter[PerformanceStatusCount] = macroRW
