package riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression

/** AdvancementRule 表示前后端共享的AdvancementRule 数据结构，包含ruleType、cutSize、thresholdScore、targetTableCount、templateKey、note。 */

final case class AdvancementRule(
    ruleType: AdvancementRuleType,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    templateKey: Option[String] = None,
    note: Option[String] = None
)
