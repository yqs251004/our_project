package riichinexus.microservices.tournament.domain.stage.functions.rules.knockout


import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutRuleConfig

/** KnockoutRuleConfigFunctions 提供KnockoutRule配置相关的领域计算、校验和转换函数。 */

private[tournament] object KnockoutRuleConfigFunctions:
  private val supportedPolicies = Set("rating", "elo", "ranking", "standings")

  def validate(config: KnockoutRuleConfig): Unit =
    require(
      supportedPolicies.contains(config.seedingPolicy.trim.toLowerCase),
      s"Unsupported knockout seeding policy: ${config.seedingPolicy}"
    )
