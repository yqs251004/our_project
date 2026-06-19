package riichinexus.microservices.tournament.objects.stage

/** StageStatus 枚举阶段状态 可使用的公开取值。 */

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
