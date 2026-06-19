package riichinexus.microservices.tournament.objects.stage

import upickle.default.{ReadWriter, readwriter}

/** StageStatus 枚举阶段状态 可使用的公开取值。 */

enum StageStatus:
  case Pending
  case Ready
  case Active
  case Completed
  case Archived

object StageStatus:
  given ReadWriter[StageStatus] = readwriter[String].bimap(_.toString, StageStatus.valueOf)
