package riichinexus.microservices.tournament.objects.tablemanagement

import upickle.default.{ReadWriter, readwriter}

/** TableStatus 枚举牌桌状态 可使用的公开取值。 */

enum TableStatus:
  case WaitingPreparation
  case InProgress
  case Scoring
  case Archived
  case AppealInProgress

object TableStatus:
  val Pending: TableStatus = WaitingPreparation
  val Finished: TableStatus = Archived

  given ReadWriter[TableStatus] = readwriter[String].bimap(_.toString, TableStatus.valueOf)
