package riichinexus.microservices.tournament.appeal.objects

/** 申诉工单的处理优先级。
  *
  * 优先级用于后台排序、逾期提醒和人工分派，帮助运营先处理会阻塞比赛继续进行的工单。
  */
enum AppealPriority:
  case Low
  case Normal
  case High
  case Critical

object AppealPriority:
  def toString(priority: AppealPriority): String =
    priority.toString

  def fromString(value: String): AppealPriority =
    AppealPriority.valueOf(value)
