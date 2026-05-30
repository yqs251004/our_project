package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.{
  AppealAttachment,
  AppealAttachmentMediaKind as DomainAppealAttachmentMediaKind,
  AppealAttachmentStorageKind as DomainAppealAttachmentStorageKind
}
import riichinexus.microservices.tournament.appeal.objects.{AppealAttachmentMediaKind, AppealAttachmentStorageKind}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

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
):
  def toAttachment: AppealAttachment =
    AppealAttachment(
      name = name,
      uri = uri,
      contentType = contentType,
      storageKind = storageKind.map(_.toDomain).getOrElse(DomainAppealAttachmentStorageKind.ExternalUrl),
      mediaKind = mediaKind.map(_.toDomain).getOrElse(DomainAppealAttachmentMediaKind.Other),
      checksum = checksum,
      checksumAlgorithm = checksumAlgorithm,
      sizeBytes = sizeBytes,
      uploadedAt = uploadedAt,
      retentionUntil = retentionUntil
    )

object AppealAttachmentRequest:
  given ReadWriter[AppealAttachmentRequest] = macroRW
