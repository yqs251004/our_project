package riichinexus.microservices.opsanalytics.objects

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
