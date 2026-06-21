package riichinexus.microservices.tournament.objects.stage.rules.progression

/** 阶段晋级规则的可序列化配置。
  *
  * 根据 `ruleType` 读取截断人数、分数阈值、目标牌桌数或模板键，供晋级预览和阶段完成时生成正选/候补名单。
  */
final case class AdvancementRule(
    ruleType: AdvancementRuleType,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    templateKey: Option[String] = None,
    note: Option[String] = None
)
