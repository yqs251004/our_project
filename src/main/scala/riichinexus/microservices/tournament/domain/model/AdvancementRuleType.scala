package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

enum AdvancementRuleType derives CanEqual:
  case SwissCut
  case KnockoutElimination
  case ScoreThreshold
  case Custom

