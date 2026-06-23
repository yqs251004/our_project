package riichinexus.microservices.tournament.appeal.objects

import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentMediaKind, AppealAttachmentStorageKind}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 返回给申诉详情页的附件展示数据。
  *
  * 视图保留预览和下载所需的名称、地址、媒体分类、大小和上传时间，同时隐藏校验值、留存期限等内部治理字段。
  */
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
