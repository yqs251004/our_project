package riichinexus.microservices.tournament.domain.model

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

enum TableStatus derives CanEqual:
  case WaitingPreparation
  case InProgress
  case Scoring
  case Archived
  case AppealInProgress

object TableStatus:
  val Pending: TableStatus = WaitingPreparation
  val Finished: TableStatus = Archived

