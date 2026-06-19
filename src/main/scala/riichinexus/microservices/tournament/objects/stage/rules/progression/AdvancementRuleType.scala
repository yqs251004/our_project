package riichinexus.microservices.tournament.objects.stage.rules.progression

import upickle.default.{ReadWriter, readwriter}

/** AdvancementRuleType 枚举AdvancementRule类型 可使用的公开取值。 */

enum AdvancementRuleType:
  case SwissCut
  case KnockoutElimination
  case ScoreThreshold
  case Custom

object AdvancementRuleType:
  given ReadWriter[AdvancementRuleType] = readwriter[String].bimap(_.toString, AdvancementRuleType.valueOf)
