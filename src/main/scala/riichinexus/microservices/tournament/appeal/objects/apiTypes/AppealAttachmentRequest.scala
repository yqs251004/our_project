package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentMediaKind, AppealAttachmentStorageKind}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 提交申诉时附带的附件元数据。
  *
  * 前端通过该结构描述附件名称、访问地址、媒体类型、存储位置和校验信息，后端会转换成领域附件并执行基础合法性校验。
  */
final case class AppealAttachmentRequest(
    name: String,
    uri: String,
    contentType: Option[String] = None,
    storageKind: Option[AppealAttachmentStorageKind] = None,
    mediaKind: Option[AppealAttachmentMediaKind] = None,
    checksum: Option[String] = None,
    checksumAlgorithm: Option[String] = None,
    sizeBytes: Option[Long] = None,
    uploadedAt: Option[Instant] = None,
    retentionUntil: Option[Instant] = None
)

object AppealAttachmentRequest:
  given ReadWriter[AppealAttachmentRequest] = macroRW
