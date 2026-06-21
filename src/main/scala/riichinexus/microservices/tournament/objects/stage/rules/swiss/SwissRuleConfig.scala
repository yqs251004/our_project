package riichinexus.microservices.tournament.objects.stage.rules.swiss

/** 瑞士轮阶段的规则配置。
  *
  * 配置控制配对策略、积分是否跨轮累计以及最大轮数，排桌和排名计算会以这些字段为准。
  */
final case class SwissRuleConfig(
    pairingMethod: SwissPairingMethod = SwissPairingMethod.BalancedElo,
    carryOverPoints: Boolean = true,
    maxRounds: Option[Int] = None
)
