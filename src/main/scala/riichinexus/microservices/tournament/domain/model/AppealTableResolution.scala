package riichinexus.microservices.tournament.domain.model

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.SeatWind

enum AppealTableResolution derives CanEqual:
  case RestorePriorState
  case ArchiveTable
  case ResumeScoring
  case ResumePlay
  case ForceReset

