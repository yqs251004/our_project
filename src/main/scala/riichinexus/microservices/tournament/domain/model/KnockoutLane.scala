package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

enum KnockoutLane derives CanEqual:
  case Championship
  case Bronze
  case Repechage

