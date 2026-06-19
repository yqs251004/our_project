package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealApplicationService
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.{AppealTicketView, ReopenAppealRequest}
import upickle.default.ReadWriter

/** 重新打开已处理的申诉工单。 */
final case class AppealReopenAPIMessage(
    appealId: String,
    operatorId: String,
    reason: String,
    note: Option[String] = None
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      resolved <- IO.delay(resolveInput)
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(resolved.operatorId)).plan(context)
      reopenedAt <- IO.realTimeInstant
      command = ReopenAppealCommand(AppealTicketId(appealId), resolved, actor, reopenedAt)
      existingTicket <- IO.blocking(
        AppealTicketTable.findById(context.connection, command.ticketId)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
      _ <- RequirePermissionPrivateAPIMessage(
        command.actor,
        Permission.ResolveAppeal,
        tournamentId = Some(existingTicket.tournamentId)
      ).plan(context)
      ticket <- IO.blocking(reopenAppeal(context.connection, command))
      _ <- RecordAuditEventsPrivateAPIMessage(reopenAppealAudit(ticket, command)).plan(context)
    yield AppealTicketView.fromDomain(ticket)

  private def resolveInput: ReopenAppealRequest =
    ReopenAppealRequest(operatorId, reason, note)

  private def reopenAppeal(
      connection: java.sql.Connection,
      command: ReopenAppealCommand
  ): AppealTicket =
    AppealApplicationService.reopenAppeal(
      connection = connection,
      ticketId = command.ticketId,
      reason = command.input.reason,
      actor = privateActor(command.actor),
      reopenedAt = command.reopenedAt,
      note = command.input.note
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private def privateActor(actor: AccessPrincipalPrivateView): AccessPrincipalPrivateView =
    actor

  private def reopenAppealAudit(
      ticket: AppealTicket,
      command: ReopenAppealCommand
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "appeal",
        aggregateId = command.ticketId.value,
        eventType = "AppealTicketReopened",
        occurredAt = command.reopenedAt,
        actorId = command.actor.playerId,
        details = Map(
          "tournamentId" -> ticket.tournamentId.value,
          "tableId" -> ticket.tableId.value,
          "reopenCount" -> ticket.reopenCount.toString
        ),
        note = command.input.note.orElse(Some(command.input.reason))
      )
    )

  private final case class ReopenAppealCommand(
      ticketId: AppealTicketId,
      input: ReopenAppealRequest,
      actor: AccessPrincipalPrivateView,
      reopenedAt: Instant
  )
