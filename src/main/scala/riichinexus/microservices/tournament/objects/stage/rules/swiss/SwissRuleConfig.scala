package riichinexus.microservices.tournament.objects.stage.rules.swiss

/** SwissRuleConfig 表示前后端共享的SwissRule配置 数据结构，包含pairingMethod、carryOverPoints、maxRounds。 */

final case class SwissRuleConfig(
    pairingMethod: SwissPairingMethod = SwissPairingMethod.BalancedElo,
    carryOverPoints: Boolean = true,
    maxRounds: Option[Int] = None
)
