package riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression

final case class AdvancementRule(
    ruleType: AdvancementRuleType,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    templateKey: Option[String] = None,
    note: Option[String] = None
) derives CanEqual
