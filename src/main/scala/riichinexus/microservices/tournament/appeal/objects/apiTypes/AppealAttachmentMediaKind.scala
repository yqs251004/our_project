package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.domain.model.{AppealAttachmentMediaKind as DomainAppealAttachmentMediaKind}
import upickle.default.*

enum AppealAttachmentMediaKind derives CanEqual:
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
