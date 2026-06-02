package riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression

import upickle.default.*

enum AdvancementRuleType:
  case SwissCut
  case KnockoutElimination
  case ScoreThreshold
  case Custom

object AdvancementRuleType:
  given ReadWriter[AdvancementRuleType] = readwriter[String].bimap(_.toString, AdvancementRuleType.valueOf)
