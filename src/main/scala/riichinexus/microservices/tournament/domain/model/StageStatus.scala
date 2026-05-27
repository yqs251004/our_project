package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

enum StageStatus derives CanEqual:
  case Pending
  case Ready
  case Active
  case Completed
  case Archived

