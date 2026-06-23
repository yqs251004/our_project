package riichinexus.microservices.tournament.appeal.api

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.tournament.mahjongcore.api.gamestate.`private`.ResetMahjongTableStatePrivateAPIMessage
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.`private`.ResetMahjongTableStateRequest

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealApplicationService
import riichinexus.microservices.tournament.appeal.domain.functions.AppealNotificationRequestFunctions
import riichinexus.microservices.tournament.appeal.domain.functions.AppealViewFunctions
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.appeal.objects.AppealTicketId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket
import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionType, AppealTableResolution}

import riichinexus.microservices.tournament.appeal.objects.apiTypes.{AdjudicateAppealRequest}
import riichinexus.microservices.tournament.appeal.objects.{AppealTicketView}
/** 裁决申诉工单并按处理结果更新牌桌。 */
final case class AppealAdjudicateAPIMessage(
    appealId: String,
    request: AdjudicateAppealRequest
) extends APIMessage[AppealTicketView]:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      adjudicatedAt <- IO.realTimeInstant
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
      ticket <- adjudicateAppeal(context.connection, requestedAppealId, actor, adjudicatedAt)
      _ <- resetMahjongCoreIfNeeded(context, ticket)
      _ <- RecordAuditEventsPrivateAPIMessage(adjudicateAppealAudit(ticket, requestedAppealId, actor, adjudicatedAt)).plan(context)
      notifications <- IO.blocking(
        AppealNotificationRequestFunctions.appealAdjudicated(
          context.connection,
          ticket,
          request.decision,
          request.tableResolution,
          request.verdict
        )
      )
      _ <- RecordBulkNotificationsPrivateAPIMessage(notifications).plan(context)
    yield AppealViewFunctions.ticketView(ticket)

  private def adjudicateAppeal(
      connection: java.sql.Connection,
      ticketId: AppealTicketId,
      actor: AccessPrincipalPrivateView,
      adjudicatedAt: Instant
  ): IO[AppealTicket] =
    AppealApplicationService.adjudicateAppeal(
      connection = connection,
      ticketId = ticketId,
      decision = request.decision,
      verdict = request.verdict,
      actor = actor,
      adjudicatedAt = adjudicatedAt,
      tableResolution = request.tableResolution,
      note = request.note
    ).map(_.getOrElse(throw NoSuchElementException("Resource not found")))

  private def resetMahjongCoreIfNeeded(
      context: ApiPlanContext,
      ticket: AppealTicket
  ): IO[Unit] =
    if request.tableResolution.contains(AppealTableResolution.ForceReset) then
      ResetMahjongTableStatePrivateAPIMessage(
        ticket.tableId.value,
        ResetMahjongTableStateRequest(
          operatorId = Some(request.operatorId),
          note = request.note.getOrElse(request.verdict)
        )
      ).plan(context).void
    else IO.unit

  private def adjudicateAppealAudit(
      ticket: AppealTicket,
      ticketId: AppealTicketId,
      actor: AccessPrincipalPrivateView,
      adjudicatedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.Appeal,
        aggregateId = ticketId.value,
        eventType = AuditEventType.AppealTicketAdjudicated,
        occurredAt = adjudicatedAt,
        actorId = actor.playerId,
        details = Map(
          StructuredEventField.toString(StructuredEventField.Decision) -> request.decision.toString,
          StructuredEventField.toString(StructuredEventField.TournamentId) -> ticket.tournamentId.value,
          StructuredEventField.toString(StructuredEventField.TableId) -> ticket.tableId.value,
          StructuredEventField.toString(StructuredEventField.TableResolution) -> request.tableResolution.map(_.toString).getOrElse("none")
        ),
        note = request.note.orElse(Some(request.verdict))
      )
    )
