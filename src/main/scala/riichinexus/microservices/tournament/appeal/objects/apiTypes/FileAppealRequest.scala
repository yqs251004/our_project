package riichinexus.microservices.tournament.appeal.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.{AppealPriority as DomainAppealPriority}
import riichinexus.microservices.tournament.appeal.objects.AppealPriority
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class FileAppealRequest(
    playerId: String,
    description: String,
    attachments: Vector[AppealAttachmentRequest] = Vector.empty,
    priority: Option[AppealPriority] = None,
    dueAt: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def priorityLevel: DomainAppealPriority =
    priority.map(_.toDomain).getOrElse(DomainAppealPriority.Normal)

  def dueAtInstant: Option[Instant] =
    dueAt.map(Instant.parse)

object FileAppealRequest:
  given ReadWriter[FileAppealRequest] = macroRW
