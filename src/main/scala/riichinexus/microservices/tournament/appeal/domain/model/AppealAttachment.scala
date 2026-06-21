package riichinexus.microservices.tournament.appeal.domain.model

import java.time.Instant

import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentMediaKind, AppealAttachmentStorageKind}
import riichinexus.system.json.JsonCodecs.given

/** 申诉工单随附的证据或补充材料。
  *
  * 附件记录面向领域层保留原始访问地址、存储方式、媒体类型、校验信息和留存时间，便于审核员复核证据且后续可以执行清理策略。
  */
final case class AppealAttachment(
    name: String,
    uri: String,
    contentType: Option[String] = None,
    storageKind: AppealAttachmentStorageKind = AppealAttachmentStorageKind.ExternalUrl,
    mediaKind: AppealAttachmentMediaKind = AppealAttachmentMediaKind.Other,
    checksum: Option[String] = None,
    checksumAlgorithm: Option[String] = None,
    sizeBytes: Option[Long] = None,
    uploadedAt: Option[Instant] = None,
    retentionUntil: Option[Instant] = None
):
  require(name.trim.nonEmpty, "Appeal attachment name cannot be empty")
  require(uri.trim.nonEmpty, "Appeal attachment uri cannot be empty")
  require(sizeBytes.forall(_ > 0L), "Appeal attachment sizeBytes must be positive when provided")
  require(
    retentionUntil.forall(retention => uploadedAt.forall(!retention.isBefore(_))),
    "Appeal attachment retentionUntil cannot be earlier than uploadedAt"
  )
