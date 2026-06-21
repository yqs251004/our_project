package riichinexus.microservices.opsanalytics.objects

/** 批量重算高级统计时可选择的补算范围。
  *
  * Full 会重建目标统计，Missing 只补缺失看板，Stale 只处理计算器版本或数据时间已过期的看板。
  */
enum AdvancedStatsBackfillMode:
  case Full
  case Missing
  case Stale

object AdvancedStatsBackfillMode:
  def toString(mode: AdvancedStatsBackfillMode): String =
    mode match
      case AdvancedStatsBackfillMode.Full    => "Full"
      case AdvancedStatsBackfillMode.Missing => "Missing"
      case AdvancedStatsBackfillMode.Stale   => "Stale"

  def fromString(value: String): Either[String, AdvancedStatsBackfillMode] =
    value.trim match
      case "Full"    => Right(AdvancedStatsBackfillMode.Full)
      case "Missing" => Right(AdvancedStatsBackfillMode.Missing)
      case "Stale"   => Right(AdvancedStatsBackfillMode.Stale)
      case other     => Left(s"Unsupported AdvancedStatsBackfillMode value: $other")
