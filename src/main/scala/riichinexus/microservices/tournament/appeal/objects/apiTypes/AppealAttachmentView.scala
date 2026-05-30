package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.AppealAttachment
import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentMediaKind, AppealAttachmentStorageKind}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AppealAttachmentView(
    name: String,
    uri: String,
    contentType: Option[String],
    storageKind: AppealAttachmentStorageKind,
    mediaKind: AppealAttachmentMediaKind,
    sizeBytes: Option[Long],
    uploadedAt: Option[String]
) derives CanEqual

object AppealAttachmentView:
  def fromDomain(attachment: AppealAttachment): AppealAttachmentView =
    AppealAttachmentView(
      name = attachment.name,
      uri = attachment.uri,
      contentType = attachment.contentType,
      storageKind = AppealAttachmentStorageKind.fromDomain(attachment.storageKind),
      mediaKind = AppealAttachmentMediaKind.fromDomain(attachment.mediaKind),
      sizeBytes = attachment.sizeBytes,
      uploadedAt = attachment.uploadedAt.map(_.toString)
    )

  given ReadWriter[AppealAttachmentView] = macroRW
