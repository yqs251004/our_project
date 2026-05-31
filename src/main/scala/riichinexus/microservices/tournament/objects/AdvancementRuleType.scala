package riichinexus.microservices.tournament.objects

import upickle.default.*

enum AdvancementRuleType derives CanEqual:
  case SwissCut
  case KnockoutElimination
  case ScoreThreshold
  case Custom

object AdvancementRuleType:
  given ReadWriter[AdvancementRuleType] = readwriter[String].bimap(_.toString, AdvancementRuleType.valueOf)
