package riichinexus.microservices.tournament.appeal.api

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealApplicationService
import riichinexus.microservices.tournament.appeal.domain.functions.AppealViewFunctions
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.appeal.objects.AppealTicketId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.{AppealTicketView}
/** 重新打开已处理的申诉工单。 */
final case class AppealReopenAPIMessage(
    appealId: String,
    operatorId: String,
    reason: String,
    note: Option[String] = None
) extends APIMessage[AppealTicketView]:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      reopenedAt <- IO.realTimeInstant
      requestedAppealId = AppealTicketId(appealId)
      existingTicket <- IO.blocking(
        AppealTicketTable.findById(context.connection, requestedAppealId)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.ResolveAppeal,
        tournamentId = Some(existingTicket.tournamentId)
      ).plan(context)
      ticket <- IO.blocking(reopenAppeal(context.connection, requestedAppealId, actor, reopenedAt))
      _ <- RecordAuditEventsPrivateAPIMessage(reopenAppealAudit(ticket, requestedAppealId, actor, reopenedAt)).plan(context)
    yield AppealViewFunctions.ticketView(ticket)

  private def reopenAppeal(
      connection: java.sql.Connection,
      ticketId: AppealTicketId,
      actor: AccessPrincipalPrivateView,
      reopenedAt: Instant
  ): AppealTicket =
    AppealApplicationService.reopenAppeal(
      connection = connection,
      ticketId = ticketId,
      reason = reason,
      actor = actor,
      reopenedAt = reopenedAt,
      note = note
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private def reopenAppealAudit(
      ticket: AppealTicket,
      ticketId: AppealTicketId,
      actor: AccessPrincipalPrivateView,
      reopenedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Appeal,
        aggregateId = ticketId.value,
        eventType = AuditEventType.AppealTicketReopened,
        occurredAt = reopenedAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.TournamentId) -> ticket.tournamentId.value,
          StructuredEventField.toString(StructuredEventField.TableId) -> ticket.tableId.value,
          StructuredEventField.toString(StructuredEventField.ReopenCount) -> ticket.reopenCount.toString
        ),
        note = note.orElse(Some(reason))
      )
    )
