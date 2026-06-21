package riichinexus.microservices.tournament.objects.stage.rules.knockout

/** 淘汰赛阶段的 bracket 生成规则。
  *
  * 配置决定签表规模、是否生成季军战、种子排序依据和是否启用复活线，是构建淘汰赛快照的主要输入。
  */
final case class KnockoutRuleConfig(
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Boolean = false,
    seedingPolicy: KnockoutSeedingPolicy = KnockoutSeedingPolicy.Rating,
    repechageEnabled: Boolean = false
)
