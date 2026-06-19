package riichinexus.microservices.tournament.domain.stage.functions.rules.knockout

import riichinexus.microservices.tournament.objects.stage.rules.knockout.{KnockoutRuleConfig, KnockoutSeedingPolicy}

/** KnockoutRuleConfigFunctions 提供KnockoutRule配置相关的领域计算、校验和转换函数。 */

private[tournament] object KnockoutRuleConfigFunctions:
  def validate(config: KnockoutRuleConfig): Unit =
    config.seedingPolicy match
      case KnockoutSeedingPolicy.Rating | KnockoutSeedingPolicy.Elo | KnockoutSeedingPolicy.Ranking | KnockoutSeedingPolicy.Standings =>
        ()
