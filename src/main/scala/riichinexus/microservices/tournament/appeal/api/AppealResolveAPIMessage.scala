package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
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

final case class AppealResolveAPIMessage(
    appealId: String,
    operatorId: String,
    verdict: String,
    note: Option[String] = None
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      resolved <- IO.blocking(resolveInput)
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(resolved.operatorId)).resolve(context.connection))
      resolvedAt <- IO.realTimeInstant
      service = context.support.tournamentAppealService
      command = ResolveAppealCommand(AppealTicketId(appealId), resolved, actor, resolvedAt)
      ticket <- IO.blocking(resolveAppeal(context.connection, service, command))
      _ <- RecordAuditEventsPrivateAPIMessage(resolveAppealAudit(ticket, command)).plan(context)
    yield AppealTicketView.fromDomain(ticket)

  private def resolveInput: ResolveAppealRequest =
    ResolveAppealRequest(operatorId, verdict, note)

  private def resolveAppeal(
      connection: java.sql.Connection,
      service: AppealApplicationService,
      command: ResolveAppealCommand
  ): AppealTicket =
    service.resolveAppeal(
      connection = connection,
      ticketId = command.ticketId,
      verdict = command.input.verdict,
      actor = command.actor,
      resolvedAt = command.resolvedAt,
      note = command.input.note
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private def resolveAppealAudit(
      ticket: AppealTicket,
      command: ResolveAppealCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "appeal",
        aggregateId = command.ticketId.value,
        eventType = "AppealTicketAdjudicated",
        occurredAt = command.resolvedAt,
        actorId = command.actor.playerId,
        details = Map(
          "decision" -> AppealDecisionType.Resolve.toString,
          "tournamentId" -> ticket.tournamentId.value,
          "tableId" -> ticket.tableId.value,
          "tableResolution" -> AppealTableResolution.RestorePriorState.toString
        ),
        note = command.input.note.orElse(Some(command.input.verdict))
      )
    )

  private final case class ResolveAppealCommand(
      ticketId: AppealTicketId,
      input: ResolveAppealRequest,
      actor: AccessPrincipal,
      resolvedAt: Instant
  )
