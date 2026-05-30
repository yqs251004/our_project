package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.microservices.tournament.domain.model.{SwissRuleConfig as DomainSwissRuleConfig}
import upickle.default.*

final case class SwissRuleConfigView(
    pairingMethod: String,
    carryOverPoints: Boolean,
    maxRounds: Option[Int]
) derives ReadWriter

object SwissRuleConfigView:
  def fromDomain(config: DomainSwissRuleConfig): SwissRuleConfigView =
    SwissRuleConfigView(
      pairingMethod = config.pairingMethod,
      carryOverPoints = config.carryOverPoints,
      maxRounds = config.maxRounds
    )
