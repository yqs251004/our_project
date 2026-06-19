package riichinexus.microservices.tournament.domain.stage.functions.rules.swiss

import riichinexus.microservices.tournament.objects.stage.rules.swiss.{SwissPairingMethod, SwissRuleConfig}

/** SwissRuleConfigFunctions 提供SwissRule配置相关的领域计算、校验和转换函数。 */

private[tournament] object SwissRuleConfigFunctions:
  def validate(config: SwissRuleConfig): Unit =
    config.pairingMethod match
      case SwissPairingMethod.BalancedElo | SwissPairingMethod.Snake => ()
