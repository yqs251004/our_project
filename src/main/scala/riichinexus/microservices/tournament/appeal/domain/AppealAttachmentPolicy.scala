package riichinexus.microservices.tournament.appeal.domain

import java.net.URI
import java.time.{Duration, Instant}

import riichinexus.microservices.tournament.appeal.domain.model.*

private object AppealAttachmentPolicy:
  private val MaxAttachmentCount = 12
  private val MaxAttachmentNameLength = 160
  private val MaxAttachmentUriLength = 2048
  private val MaxAttachmentBytes = 150L * 1024L * 1024L
  private val MaxRetentionWindow = Duration.ofDays(365)
  private val AllowedSchemesByStorageKind: Map[AppealAttachmentStorageKind, Set[String]] = Map(
    AppealAttachmentStorageKind.ExternalUrl -> Set("https", "http"),
    AppealAttachmentStorageKind.ObjectStore -> Set("s3", "gs", "riichinexus-object"),
    AppealAttachmentStorageKind.SignedUrl -> Set("https"),
    AppealAttachmentStorageKind.InternalReference -> Set("riichinexus", "app")
  )
  private val AllowedContentTypesByMediaKind: Map[AppealAttachmentMediaKind, Set[String]] = Map(
    AppealAttachmentMediaKind.Image -> Set("image/png", "image/jpeg", "image/webp", "image/gif"),
    AppealAttachmentMediaKind.Video -> Set("video/mp4", "video/webm", "video/quicktime"),
    AppealAttachmentMediaKind.Document -> Set("application/pdf", "text/plain", "text/markdown"),
    AppealAttachmentMediaKind.Log -> Set("text/plain", "application/json", "text/csv"),
    AppealAttachmentMediaKind.Archive -> Set("application/zip", "application/gzip", "application/x-7z-compressed"),
    AppealAttachmentMediaKind.Other -> Set.empty
  )
  private val SupportedChecksumAlgorithms: Map[String, Int] = Map(
    "sha-256" -> 64,
    "sha-512" -> 128
  )

  def validate(
      attachments: Vector[AppealAttachment],
      createdAt: Instant
  ): Vector[AppealAttachment] =
    require(attachments.size <= MaxAttachmentCount, s"Appeals can carry at most $MaxAttachmentCount attachments")
    attachments.zipWithIndex.map { case (attachment, index) =>
      validateAttachment(attachment, createdAt, index + 1)
    }

  private def validateAttachment(
      attachment: AppealAttachment,
      createdAt: Instant,
      position: Int
  ): AppealAttachment =
    val normalizedName = attachment.name.trim
    val normalizedUri = attachment.uri.trim
    val normalizedContentType = attachment.contentType.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val normalizedChecksum = attachment.checksum.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val normalizedAlgorithm = attachment.checksumAlgorithm.map(_.trim.toLowerCase).filter(_.nonEmpty)

    require(normalizedName.nonEmpty, s"Appeal attachment #$position name cannot be empty")
    require(normalizedName.length <= MaxAttachmentNameLength, s"Appeal attachment #$position name is too long")
    require(normalizedUri.nonEmpty, s"Appeal attachment #$position uri cannot be empty")
    require(normalizedUri.length <= MaxAttachmentUriLength, s"Appeal attachment #$position uri is too long")

    val parsedUri =
      try URI(normalizedUri)
      catch
        case _: IllegalArgumentException =>
          throw IllegalArgumentException(s"Appeal attachment #$position uri is not a valid URI")

    val scheme = Option(parsedUri.getScheme).map(_.trim.toLowerCase)
      .getOrElse(throw IllegalArgumentException(s"Appeal attachment #$position uri must include a scheme"))
    require(
      AllowedSchemesByStorageKind.getOrElse(attachment.storageKind, Set.empty).contains(scheme),
      s"Appeal attachment #$position scheme '$scheme' is not allowed for ${attachment.storageKind}"
    )

    attachment.sizeBytes.foreach { sizeBytes =>
      require(sizeBytes <= MaxAttachmentBytes, s"Appeal attachment #$position exceeds $MaxAttachmentBytes bytes")
    }

    normalizedAlgorithm match
      case Some(algorithm) =>
        val expectedLength =
          SupportedChecksumAlgorithms.getOrElse(
            algorithm,
            throw IllegalArgumentException(
              s"Appeal attachment #$position checksum algorithm '$algorithm' is unsupported"
            )
          )
        val checksum =
          normalizedChecksum.getOrElse(
            throw IllegalArgumentException(s"Appeal attachment #$position checksum is required")
          )
        require(checksum.forall(isHexChar), s"Appeal attachment #$position checksum must be hexadecimal")
        require(
          checksum.length == expectedLength,
          s"Appeal attachment #$position checksum length does not match algorithm '$algorithm'"
        )
      case None =>
        require(
          normalizedChecksum.isEmpty,
          s"Appeal attachment #$position checksumAlgorithm is required when checksum is provided"
        )

    normalizedContentType.foreach { contentType =>
      val allowedContentTypes = AllowedContentTypesByMediaKind.getOrElse(attachment.mediaKind, Set.empty)
      require(
        allowedContentTypes.isEmpty || allowedContentTypes.contains(contentType),
        s"Appeal attachment #$position contentType '$contentType' is not allowed for ${attachment.mediaKind}"
      )
    }

    attachment.uploadedAt.foreach { uploadedAt =>
      require(
        !uploadedAt.isAfter(createdAt.plus(Duration.ofHours(1))),
        s"Appeal attachment #$position uploadedAt cannot be unreasonably later than appeal creation"
      )
    }
    attachment.retentionUntil.foreach { retentionUntil =>
      require(
        !retentionUntil.isBefore(createdAt),
        s"Appeal attachment #$position retentionUntil cannot be earlier than appeal creation"
      )
      require(
        !retentionUntil.isAfter(createdAt.plus(MaxRetentionWindow)),
        s"Appeal attachment #$position retentionUntil exceeds the maximum retention window"
      )
    }

    attachment.copy(
      name = normalizedName,
      uri = normalizedUri,
      contentType = normalizedContentType,
      checksum = normalizedChecksum,
      checksumAlgorithm = normalizedAlgorithm
    )

  private def isHexChar(char: Char): Boolean =
    (char >= '0' && char <= '9') ||
      (char >= 'a' && char <= 'f')
