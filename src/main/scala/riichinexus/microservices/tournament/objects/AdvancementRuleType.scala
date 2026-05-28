package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{AdvancementRuleType as DomainAdvancementRuleType}
import upickle.default.*

enum AdvancementRuleType derives CanEqual:
  case SwissCut
  case KnockoutElimination
  case ScoreThreshold
  case Custom

  def toDomain: DomainAdvancementRuleType =
    DomainAdvancementRuleType.valueOf(toString)

object AdvancementRuleType:
  given ReadWriter[AdvancementRuleType] = readwriter[String].bimap(_.toString, AdvancementRuleType.valueOf)

  def fromDomain(ruleType: DomainAdvancementRuleType): AdvancementRuleType =
    AdvancementRuleType.valueOf(ruleType.toString)
