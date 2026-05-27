package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

enum StageFormat derives CanEqual:
  case Swiss
  case Knockout
  case RoundRobin
  case Finals
  case Custom

