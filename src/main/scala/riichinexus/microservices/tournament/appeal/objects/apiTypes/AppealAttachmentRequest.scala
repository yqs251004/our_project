package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AppealAttachmentRequest(
    name: String,
    uri: String,
    contentType: Option[String] = None,
    storageKind: Option[String] = None,
    mediaKind: Option[String] = None,
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
      storageKind = storageKind.map(AppealAttachmentStorageKind.valueOf).getOrElse(AppealAttachmentStorageKind.ExternalUrl),
      mediaKind = mediaKind.map(AppealAttachmentMediaKind.valueOf).getOrElse(AppealAttachmentMediaKind.Other),
      checksum = checksum,
      checksumAlgorithm = checksumAlgorithm,
      sizeBytes = sizeBytes,
      uploadedAt = uploadedAt,
      retentionUntil = retentionUntil
    )

object AppealAttachmentRequest:
  given ReadWriter[AppealAttachmentRequest] = macroRW

