package riichinexus.microservices.tournament.objects

import upickle.default.*

enum TableStatus derives CanEqual:
  case WaitingPreparation
  case InProgress
  case Scoring
  case Archived
  case AppealInProgress

object TableStatus:
  val Pending: TableStatus = WaitingPreparation
  val Finished: TableStatus = Archived

  given ReadWriter[TableStatus] = readwriter[String].bimap(_.toString, TableStatus.valueOf)
