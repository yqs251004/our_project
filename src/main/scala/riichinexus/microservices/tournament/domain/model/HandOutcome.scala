package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

enum HandOutcome derives CanEqual:
  case Tsumo
  case Ron
  case ExhaustiveDraw
  case AbortiveDraw

