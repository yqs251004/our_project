package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{AdvancementRule as DomainAdvancementRule}
import upickle.default.*

final case class AdvancementRuleView(
    ruleType: String,
    cutSize: Option[Int],
    thresholdScore: Option[Int],
    targetTableCount: Option[Int],
    templateKey: Option[String],
    note: Option[String]
) derives ReadWriter

object AdvancementRuleView:
  def fromDomain(rule: DomainAdvancementRule): AdvancementRuleView =
    AdvancementRuleView(
      ruleType = rule.ruleType.toString,
      cutSize = rule.cutSize,
      thresholdScore = rule.thresholdScore,
      targetTableCount = rule.targetTableCount,
      templateKey = rule.templateKey,
      note = rule.note
    )
