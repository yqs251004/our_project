package riichinexus.microservices.tournament.objects

import riichinexus.domain.model.{KnockoutRuleConfig as DomainKnockoutRuleConfig}
import upickle.default.*

final case class KnockoutRuleConfigView(
    bracketSize: Option[Int],
    thirdPlaceMatch: Boolean,
    seedingPolicy: String,
    repechageEnabled: Boolean
) derives ReadWriter

object KnockoutRuleConfigView:
  def fromDomain(config: DomainKnockoutRuleConfig): KnockoutRuleConfigView =
    KnockoutRuleConfigView(
      bracketSize = config.bracketSize,
      thirdPlaceMatch = config.thirdPlaceMatch,
      seedingPolicy = config.seedingPolicy,
      repechageEnabled = config.repechageEnabled
    )
