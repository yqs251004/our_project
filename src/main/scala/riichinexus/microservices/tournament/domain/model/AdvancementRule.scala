package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

final case class AdvancementRule(
    ruleType: AdvancementRuleType,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    templateKey: Option[String] = None,
    note: Option[String] = None
) derives CanEqual

object AdvancementRule:
  def defaultFor(format: StageFormat): AdvancementRule =
    format match
      case StageFormat.Swiss =>
        AdvancementRule(AdvancementRuleType.SwissCut, cutSize = Some(16))
      case StageFormat.Knockout =>
        AdvancementRule(AdvancementRuleType.KnockoutElimination, targetTableCount = Some(1))
      case StageFormat.RoundRobin =>
        AdvancementRule(AdvancementRuleType.ScoreThreshold, thresholdScore = Some(0))
      case StageFormat.Finals =>
        AdvancementRule(AdvancementRuleType.KnockoutElimination, targetTableCount = Some(1))
      case StageFormat.Custom =>
        AdvancementRule(AdvancementRuleType.Custom, note = Some("custom policy"))

