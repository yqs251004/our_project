package riichinexus.microservices.tournament.appeal.objects

import riichinexus.microservices.tournament.appeal.domain.model.{AppealAttachmentStorageKind as DomainAppealAttachmentStorageKind}
import upickle.default.*

enum AppealAttachmentStorageKind:
  case ExternalUrl
  case ObjectStore
  case SignedUrl
  case InternalReference

  def toDomain: DomainAppealAttachmentStorageKind =
    DomainAppealAttachmentStorageKind.valueOf(toString)

object AppealAttachmentStorageKind:
  given ReadWriter[AppealAttachmentStorageKind] =
    readwriter[String].bimap(_.toString, AppealAttachmentStorageKind.valueOf)

  def fromDomain(storageKind: DomainAppealAttachmentStorageKind): AppealAttachmentStorageKind =
    AppealAttachmentStorageKind.valueOf(storageKind.toString)
