package riichinexus.microservices.tournament.objects.stage.rules.knockout

/** KnockoutRuleConfig 表示前后端共享的KnockoutRule配置 数据结构，包含bracketSize、thirdPlaceMatch、seedingPolicy、repechageEnabled。 */

final case class KnockoutRuleConfig(
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Boolean = false,
    seedingPolicy: String = "rating",
    repechageEnabled: Boolean = false
)
