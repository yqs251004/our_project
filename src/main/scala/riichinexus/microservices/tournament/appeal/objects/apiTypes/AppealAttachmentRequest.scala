package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentMediaKind, AppealAttachmentStorageKind}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AppealAttachmentRequest 表示申诉附件请求 的前端请求参数。 */

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
