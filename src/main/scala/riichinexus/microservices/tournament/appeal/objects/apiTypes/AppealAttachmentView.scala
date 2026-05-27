package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AppealAttachmentView(
    name: String,
    uri: String,
    contentType: Option[String],
    storageKind: String,
    mediaKind: String,
    sizeBytes: Option[Long],
    uploadedAt: Option[String]
) derives CanEqual

object AppealAttachmentView:
  def fromDomain(attachment: AppealAttachment): AppealAttachmentView =
    AppealAttachmentView(
      name = attachment.name,
      uri = attachment.uri,
      contentType = attachment.contentType,
      storageKind = attachment.storageKind.toString,
      mediaKind = attachment.mediaKind.toString,
      sizeBytes = attachment.sizeBytes,
      uploadedAt = attachment.uploadedAt.map(_.toString)
    )

  given ReadWriter[AppealAttachmentView] = macroRW

