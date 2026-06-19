package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.microservices.tournament.appeal.domain.model.AppealAttachment
import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentMediaKind, AppealAttachmentStorageKind}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AppealAttachmentView 表示申诉附件视图 的前端展示视图。 */

final case class AppealAttachmentView(
    name: String,
    uri: String,
    contentType: Option[String],
    storageKind: AppealAttachmentStorageKind,
    mediaKind: AppealAttachmentMediaKind,
    sizeBytes: Option[Long],
    uploadedAt: Option[String]
)

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
