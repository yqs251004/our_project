package riichinexus.microservices.tournament.appeal.domain.model

import java.time.Instant

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
) derives CanEqual:
  require(name.trim.nonEmpty, "Appeal attachment name cannot be empty")
  require(uri.trim.nonEmpty, "Appeal attachment uri cannot be empty")
  require(sizeBytes.forall(_ > 0L), "Appeal attachment sizeBytes must be positive when provided")
  require(
    retentionUntil.forall(retention => uploadedAt.forall(!retention.isBefore(_))),
    "Appeal attachment retentionUntil cannot be earlier than uploadedAt"
  )
