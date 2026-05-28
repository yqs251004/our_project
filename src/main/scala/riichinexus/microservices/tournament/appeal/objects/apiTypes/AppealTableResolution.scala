package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.domain.model.{AppealTableResolution as DomainAppealTableResolution}
import upickle.default.*

enum AppealTableResolution derives CanEqual:
  case RestorePriorState
  case ArchiveTable
  case ResumeScoring
  case ResumePlay
  case ForceReset

  def toDomain: DomainAppealTableResolution =
    DomainAppealTableResolution.valueOf(toString)

object AppealTableResolution:
  given ReadWriter[AppealTableResolution] =
    readwriter[String].bimap(_.toString, AppealTableResolution.valueOf)

  def fromDomain(resolution: DomainAppealTableResolution): AppealTableResolution =
    AppealTableResolution.valueOf(resolution.toString)
