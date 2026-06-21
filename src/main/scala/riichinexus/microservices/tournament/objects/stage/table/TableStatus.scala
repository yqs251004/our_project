package riichinexus.microservices.tournament.objects.stage.table

/** 赛事牌桌从等待开局到归档的状态。
  *
  * 状态决定前端显示准备控件、对局入口、计分面板、申诉处理还是归档结果。
  */
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
