package riichinexus.microservices.tournament.appeal.objects

import riichinexus.microservices.tournament.appeal.domain.model.{AppealAttachmentMediaKind as DomainAppealAttachmentMediaKind}
import upickle.default.{ReadWriter, readwriter}

/** AppealAttachmentMediaKind 枚举申诉附件媒体类型 可使用的公开取值。 */

enum AppealAttachmentMediaKind:
  case Image
  case Video
  case Document
  case Log
  case Archive
  case Other

  def toDomain: DomainAppealAttachmentMediaKind =
    DomainAppealAttachmentMediaKind.valueOf(toString)

object AppealAttachmentMediaKind:
  given ReadWriter[AppealAttachmentMediaKind] =
    readwriter[String].bimap(_.toString, AppealAttachmentMediaKind.valueOf)

  def fromDomain(mediaKind: DomainAppealAttachmentMediaKind): AppealAttachmentMediaKind =
    AppealAttachmentMediaKind.valueOf(mediaKind.toString)
