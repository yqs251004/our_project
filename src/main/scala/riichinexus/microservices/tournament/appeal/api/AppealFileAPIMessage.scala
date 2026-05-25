package riichinexus.microservices.tournament.appeal.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentAppealModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealFileAPIMessage(
    tableId: String,
    request: FileAppealRequest
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- IO(context.support.principal(request.player))
      createdAt <- IO.realTimeInstant
      module = context.support.tournamentAppealModule
      command <- IO(resolveCommand(actor, createdAt))
      ticket <- IO(fileAppeal(module, command))
    yield AppealTicketView.fromDomain(ticket)

  private def resolveCommand(actor: AccessPrincipal, createdAt: Instant): FileAppealCommand =
    FileAppealCommand(
      tableId = TableId(tableId),
      openedBy = request.player,
      description = request.description,
      attachments = request.attachments.map(_.toAttachment),
      priority = request.priorityLevel,
      dueAt = request.dueAtInstant,
      actor = actor,
      createdAt = createdAt
    )

  private def fileAppeal(
      module: TournamentAppealModuleContext,
      command: FileAppealCommand
  ): AppealTicket =
    module.service.fileAppeal(
      tableId = command.tableId,
      openedBy = command.openedBy,
      description = command.description,
      attachments = command.attachments,
      priority = command.priority,
      dueAt = command.dueAt,
      actor = command.actor,
      createdAt = command.createdAt
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private final case class FileAppealCommand(
      tableId: TableId,
      openedBy: PlayerId,
      description: String,
      attachments: Vector[AppealAttachment],
      priority: AppealPriority,
      dueAt: Option[Instant],
      actor: AccessPrincipal,
      createdAt: Instant
  )
