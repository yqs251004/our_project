package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{StageStatus as DomainStageStatus}
import upickle.default.*

enum StageStatus derives CanEqual:
  case Pending
  case Ready
  case Active
  case Completed
  case Archived

  def toDomain: DomainStageStatus =
    DomainStageStatus.valueOf(toString)

object StageStatus:
  given ReadWriter[StageStatus] = readwriter[String].bimap(_.toString, StageStatus.valueOf)

  def fromDomain(status: DomainStageStatus): StageStatus =
    StageStatus.valueOf(status.toString)
