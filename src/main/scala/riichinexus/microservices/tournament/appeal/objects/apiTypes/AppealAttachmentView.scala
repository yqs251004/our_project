package riichinexus.microservices.tournament.appeal.objects.apiTypes

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
  given ReadWriter[AppealAttachmentView] = macroRW
