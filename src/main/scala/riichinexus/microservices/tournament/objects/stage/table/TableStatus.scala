package riichinexus.microservices.tournament.objects.stage.table

/** TableStatus 枚举牌桌状态 可使用的公开取值。 */

enum TableStatus:
  case WaitingPreparation
  case InProgress
  case Scoring
  case Archived
  case AppealInProgress

object TableStatus:
  def toString(status: TableStatus): String =
    status.toString

  def fromString(value: String): TableStatus =
    TableStatus.valueOf(value)
