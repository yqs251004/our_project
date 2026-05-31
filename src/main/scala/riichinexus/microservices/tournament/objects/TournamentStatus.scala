package riichinexus.microservices.tournament.objects

import upickle.default.*

enum TournamentStatus derives CanEqual:
  case Draft
  case RegistrationOpen
  case Scheduled
  case InProgress
  case Completed
  case Cancelled
  case Archived

object TournamentStatus:
  given ReadWriter[TournamentStatus] = readwriter[String].bimap(_.toString, TournamentStatus.valueOf)
