package riichinexus.microservices.tournament.objects.stage.rules.progression

/** AdvancementRuleType 枚举AdvancementRule类型 可使用的公开取值。 */

enum AdvancementRuleType:
  case SwissCut
  case KnockoutElimination
  case ScoreThreshold
  case Custom

object AdvancementRuleType:
  def toString(ruleType: AdvancementRuleType): String =
    ruleType.toString

  def fromString(value: String): AdvancementRuleType =
    AdvancementRuleType.valueOf(value)
