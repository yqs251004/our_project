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
import riichinexus.microservices.tournament.appeal.domain.model.{
  AppealDecisionType as DomainAppealDecisionType,
  AppealTableResolution as DomainAppealTableResolution,
  AppealTicket
}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealAdjudicateAPIMessage(
    appealId: String,
    request: AdjudicateAppealRequest
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(request.operatorId)).resolve(context.connection))
      adjudicatedAt <- IO.realTimeInstant
      service = AppealApplicationService(AuthorizationPolicyFunctions.strict)
      command <- IO.blocking(resolveCommand(actor, adjudicatedAt))
      ticket <- IO.blocking(adjudicateAppeal(context.connection, service, command))
      _ <- RecordAuditEventsPrivateAPIMessage(adjudicateAppealAudit(ticket, command)).plan(context)
    yield AppealTicketView.fromDomain(ticket)

  private def resolveCommand(actor: AccessPrincipal, adjudicatedAt: Instant): AdjudicateAppealCommand =
    AdjudicateAppealCommand(
      ticketId = AppealTicketId(appealId),
      decision = request.decision.toDomain,
      verdict = request.verdict,
      actor = actor,
      tableResolution = request.tableResolution.map(_.toDomain),
      note = request.note,
      adjudicatedAt = adjudicatedAt
    )

  private def adjudicateAppeal(
      connection: java.sql.Connection,
      service: AppealApplicationService,
      command: AdjudicateAppealCommand
  ): AppealTicket =
    service.adjudicateAppeal(
      connection = connection,
      ticketId = command.ticketId,
      decision = command.decision,
      verdict = command.verdict,
      actor = command.actor,
      adjudicatedAt = command.adjudicatedAt,
      tableResolution = command.tableResolution,
      note = command.note
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private def adjudicateAppealAudit(
      ticket: AppealTicket,
      command: AdjudicateAppealCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "appeal",
        aggregateId = command.ticketId.value,
        eventType = "AppealTicketAdjudicated",
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

  private final case class AdjudicateAppealCommand(
      ticketId: AppealTicketId,
      decision: DomainAppealDecisionType,
      verdict: String,
      actor: AccessPrincipal,
      tableResolution: Option[DomainAppealTableResolution],
      note: Option[String],
      adjudicatedAt: Instant
  )
