package riichinexus.microservices.tournament.appeal.api

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealFileAPIMessage(
    tableId: String,
    playerId: String,
    description: String,
    attachments: Vector[AppealAttachmentRequest] = Vector.empty,
    priority: Option[String] = None,
    dueAt: Option[String] = None
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    IO {
      val request = FileAppealRequest(playerId, description, attachments, priority, dueAt)
      context.support.tournamentAppealModule.service.fileAppeal(
        tableId = TableId(tableId),
        openedBy = request.player,
        description = request.description,
        attachments = request.attachments.map(_.toAttachment),
        priority = request.priorityLevel,
        dueAt = request.dueAtInstant,
        actor = context.support.principal(request.player)
      ).map(AppealTicketView.fromDomain)
        .getOrElse(throw java.util.NoSuchElementException("Resource not found"))
    }
