package riichinexus.microservices.auth.api
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import java.time.Duration
import java.time.Instant

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

final case class CreateGuestSessionAuthAPIMessage(
    displayName: Option[String] = None,
    ttlHours: Option[Int] = None,
    deviceFingerprint: Option[String] = None
) extends APIMessage[GuestSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestSessionResponse] =
    for
      createdAt <- IO.realTimeInstant
      input = resolveInput
      command = CreateGuestSessionCommand(
        input = input,
        createdAt = createdAt
      )
      savedSession <- IO.blocking {
        {
          createGuestSession(context.connection, command)
        }
      }
      _ <- RecordAuditEventsPrivateAPIMessage(createGuestSessionAudit(savedSession, command)).plan(context)
    yield guestSessionResponse(savedSession)

  private def createGuestSession(
      connection: java.sql.Connection,
      command: CreateGuestSessionCommand
  ): GuestAccessSession =
    val session = GuestAccessSessionFunctions.create(
      id = AuthIdGenerator.guestSessionId(),
      createdAt = command.createdAt,
      displayName = command.input.displayName,
      ttl = command.input.ttl,
      deviceFingerprint = command.input.deviceFingerprint
    )
    GuestSessionTable.save(connection, session)

  private def createGuestSessionAudit(
      savedSession: GuestAccessSession,
      command: CreateGuestSessionCommand
  ): Vector[AuditEvent] =
    Vector(
      AuditEvent(
        id = AuditIdGenerator.auditEventId(),
        aggregateType = "guest-session",
        aggregateId = savedSession.id.value,
        eventType = "GuestSessionCreated",
        occurredAt = command.createdAt,
        actorId = None,
        details = Map(
          "expiresAt" -> savedSession.expiresAt.toString,
          "deviceFingerprint" -> savedSession.deviceFingerprint.getOrElse("none")
        ),
        note = None
      )
    )

  private def resolveInput: ResolvedGuestSessionInput =
    ttlHours.foreach(hours => require(hours > 0, "Guest session ttlHours must be positive"))
    ResolvedGuestSessionInput(
      displayName = displayName.map(_.trim).filter(_.nonEmpty).getOrElse("guest"),
      ttl = Duration.ofHours(ttlHours.getOrElse(24 * 30).toLong),
      deviceFingerprint = deviceFingerprint
    )

  private def guestSessionResponse(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )

  private final case class CreateGuestSessionCommand(
      input: ResolvedGuestSessionInput,
      createdAt: Instant
  )

  private final case class ResolvedGuestSessionInput(
      displayName: String,
      ttl: Duration,
      deviceFingerprint: Option[String]
  )
