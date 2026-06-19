package riichinexus.microservices.tournament.appeal.objects

import riichinexus.microservices.tournament.appeal.domain.model.{AppealTableResolution as DomainAppealTableResolution}
import upickle.default.{ReadWriter, readwriter}

/** AppealTableResolution 枚举申诉牌桌处理结果 可使用的公开取值。 */

enum AppealTableResolution:
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
