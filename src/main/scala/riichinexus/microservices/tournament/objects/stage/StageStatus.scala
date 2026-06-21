package riichinexus.microservices.tournament.objects.stage

/** 赛事阶段从待配置到归档的生命周期状态。
  *
  * 阶段状态决定能否提交阵容、排桌、开局、生成排名或进入下一阶段，是赛事流程控制的核心信号。
  */
enum StageStatus:
  case Pending
  case Ready
  case Active
  case Completed
  case Archived

object StageStatus:
  def toString(status: StageStatus): String =
    status.toString

  def fromString(value: String): StageStatus =
    StageStatus.valueOf(value)
