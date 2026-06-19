package riichinexus.microservices.tournament.domain.stage.functions.rules.swiss


import riichinexus.microservices.tournament.objects.stage.rules.swiss.SwissRuleConfig

/** SwissRuleConfigFunctions 提供SwissRule配置相关的领域计算、校验和转换函数。 */

private[tournament] object SwissRuleConfigFunctions:
  private val supportedPairingMethods = Set("balanced-elo", "snake")

  def validate(config: SwissRuleConfig): Unit =
    require(
      supportedPairingMethods.contains(config.pairingMethod.trim.toLowerCase),
      s"Unsupported swiss pairing method: ${config.pairingMethod}"
    )
