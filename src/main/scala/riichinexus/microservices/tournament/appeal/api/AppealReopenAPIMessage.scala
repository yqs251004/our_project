package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealReopenAPIMessage(
    appealId: String,
    operatorId: String,
    reason: String,
    note: Option[String] = None
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      resolved <- IO.blocking(resolveInput)
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(resolved.operatorId)).resolve(context.connection))
      reopenedAt <- IO.realTimeInstant
      service = AppealApplicationService(AuthorizationPolicyFunctions.strict)
      command = ReopenAppealCommand(AppealTicketId(appealId), resolved, actor, reopenedAt)
      ticket <- IO.blocking(reopenAppeal(context.connection, service, command))
      _ <- RecordAuditEventsPrivateAPIMessage(reopenAppealAudit(ticket, command)).plan(context)
    yield AppealTicketView.fromDomain(ticket)

  private def resolveInput: ReopenAppealRequest =
    ReopenAppealRequest(operatorId, reason, note)

  private def reopenAppeal(
      connection: java.sql.Connection,
      service: AppealApplicationService,
      command: ReopenAppealCommand
  ): AppealTicket =
    service.reopenAppeal(
      connection = connection,
      ticketId = command.ticketId,
      reason = command.input.reason,
      actor = command.actor,
      reopenedAt = command.reopenedAt,
      note = command.input.note
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private def reopenAppealAudit(
      ticket: AppealTicket,
      command: ReopenAppealCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
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
      actor: AccessPrincipal,
      reopenedAt: Instant
  )
