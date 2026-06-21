package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.tournament.mahjongcore.api.`private`.ResetMahjongTableStatePrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealApplicationService
import riichinexus.microservices.tournament.appeal.domain.functions.AppealNotificationRequestFunctions
import riichinexus.microservices.tournament.appeal.domain.functions.AppealViewFunctions
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket
import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionType, AppealTableResolution}

import riichinexus.microservices.tournament.appeal.objects.apiTypes.{AdjudicateAppealRequest, AppealTicketView}
/** 裁决申诉工单并按处理结果更新牌桌。 */
final case class AppealAdjudicateAPIMessage(
    appealId: String,
    request: AdjudicateAppealRequest
) extends APIMessage[AppealTicketView]:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      adjudicatedAt <- IO.realTimeInstant
      command <- IO.delay(resolveCommand(actor, adjudicatedAt))
      existingTicket <- IO.blocking(
        AppealTicketTable.findById(context.connection, command.ticketId)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
      _ <- RequirePermissionPrivateAPIMessage(
        command.actor,
        Permission.ResolveAppeal,
        tournamentId = Some(existingTicket.tournamentId)
      ).plan(context)
      ticket <- adjudicateAppeal(context.connection, command)
      _ <- resetMahjongCoreIfNeeded(context, ticket, command)
      _ <- RecordAuditEventsPrivateAPIMessage(adjudicateAppealAudit(ticket, command)).plan(context)
      notifications <- IO.blocking(
        AppealNotificationRequestFunctions.appealAdjudicated(
          context.connection,
          ticket,
          command.decision,
          command.tableResolution,
          command.verdict
        )
      )
      _ <- RecordBulkNotificationsPrivateAPIMessage(notifications).plan(context)
    yield AppealViewFunctions.ticketView(ticket)

  private def resolveCommand(actor: AccessPrincipalPrivateView, adjudicatedAt: Instant): AdjudicateAppealCommand =
    AdjudicateAppealCommand(
      ticketId = AppealTicketId(appealId),
      decision = request.decision,
      verdict = request.verdict,
      actor = actor,
      tableResolution = request.tableResolution,
      note = request.note,
      adjudicatedAt = adjudicatedAt
    )

  private def adjudicateAppeal(
      connection: java.sql.Connection,
      command: AdjudicateAppealCommand
  ): IO[AppealTicket] =
    AppealApplicationService.adjudicateAppeal(
      connection = connection,
      ticketId = command.ticketId,
      decision = command.decision,
      verdict = command.verdict,
      actor = privateActor(command.actor),
      adjudicatedAt = command.adjudicatedAt,
      tableResolution = command.tableResolution,
      note = command.note
    ).map(_.getOrElse(throw NoSuchElementException("Resource not found")))

  private def privateActor(actor: AccessPrincipalPrivateView): AccessPrincipalPrivateView =
    actor

  private def resetMahjongCoreIfNeeded(
      context: ApiPlanContext,
      ticket: AppealTicket,
      command: AdjudicateAppealCommand
  ): IO[Unit] =
    if command.tableResolution.contains(AppealTableResolution.ForceReset) then
      IO.blocking {
        ResetMahjongTableStatePrivateAPIMessage.resetAndSave(context.connection, ticket.tableId)
        ()
      }
    else IO.unit

  private def adjudicateAppealAudit(
      ticket: AppealTicket,
      command: AdjudicateAppealCommand
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "appeal",
        aggregateId = command.ticketId.value,
        eventType = AuditEventType.AppealTicketAdjudicated,
        occurredAt = command.adjudicatedAt,
        actorId = command.actor.playerId,
        details = Map(
          "decision" -> command.decision.toString,
          "tournamentId" -> ticket.tournamentId.value,
          "tableId" -> ticket.tableId.value,
          "tableResolution" -> command.tableResolution.map(_.toString).getOrElse("none")
        ),
        note = command.note.orElse(Some(command.verdict))
      )
    )

  /** 裁定申诉工单并可同步处理牌桌状态的内部命令。 */
  private final case class AdjudicateAppealCommand(
      ticketId: AppealTicketId,
      decision: AppealDecisionType,
      verdict: String,
      actor: AccessPrincipalPrivateView,
      tableResolution: Option[AppealTableResolution],
      note: Option[String],
      adjudicatedAt: Instant
  )
