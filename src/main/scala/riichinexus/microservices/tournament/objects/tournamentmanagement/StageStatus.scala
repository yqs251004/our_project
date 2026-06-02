package riichinexus.microservices.tournament.objects.tournamentmanagement

import upickle.default.*

enum StageStatus:
  case Pending
  case Ready
  case Active
  case Completed
  case Archived

object StageStatus:
  given ReadWriter[StageStatus] = readwriter[String].bimap(_.toString, StageStatus.valueOf)
