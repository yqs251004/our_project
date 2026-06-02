package riichinexus.microservices.auth.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
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
import riichinexus.microservices.auth.domain.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
import upickle.default.*

final case class RevokeGuestSessionAuthAPIMessage(
    sessionId: String,
    reason: Option[String] = None
) extends APIMessage[GuestSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestSessionResponse] =
    for
      revokedAt <- IO.realTimeInstant
      input = resolveInput
      command = RevokeGuestSessionCommand(
        input = input,
        revokedAt = revokedAt
      )
      updated <- IO.blocking {
        {
          revokeGuestSession(context.connection, command)
        }
      }
      _ <- RecordAuditEventsPrivateAPIMessage(revokeGuestSessionAudit(updated, command)).plan(context)
    yield guestSessionResponse(updated)

  private def revokeGuestSession(
      connection: java.sql.Connection,
      command: RevokeGuestSessionCommand
  ): GuestAccessSession =
    val session = GuestSessionTable.findById(connection, command.input.sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${command.input.sessionId.value} was not found"))
    GuestSessionTable.save(
      connection,
      GuestAccessSessionFunctions.revoke(session, command.input.reason, command.revokedAt)
    )

  private def revokeGuestSessionAudit(
      updated: GuestAccessSession,
      command: RevokeGuestSessionCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "guest-session",
        aggregateId = updated.id.value,
        eventType = "GuestSessionRevoked",
        occurredAt = command.revokedAt,
        actorId = None,
        details = Map("reason" -> updated.revokedReason.getOrElse(command.input.reason)),
        note = None
      )
    )

  private def resolveInput: ResolvedRevokeGuestSessionInput =
    ResolvedRevokeGuestSessionInput(
      sessionId = GuestSessionId(sessionId),
      reason = reason.filter(_.trim.nonEmpty).getOrElse("revoked-by-operator")
    )

  private def guestSessionResponse(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )

  private final case class RevokeGuestSessionCommand(
      input: ResolvedRevokeGuestSessionInput,
      revokedAt: Instant
  )

  private final case class ResolvedRevokeGuestSessionInput(
      sessionId: GuestSessionId,
      reason: String
  )
