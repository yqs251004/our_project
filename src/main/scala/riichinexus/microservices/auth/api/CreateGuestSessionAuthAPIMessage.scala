package riichinexus.microservices.auth.api
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import java.time.Duration
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.domain.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.model.GuestAccessSession
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
/** 创建游客访问会话。 */
final case class CreateGuestSessionAuthAPIMessage(
    displayName: Option[String] = None,
    ttlHours: Option[Int] = None,
    deviceFingerprint: Option[String] = None
) extends APIMessage[GuestSessionResponse]:

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
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = "guest-session",
        aggregateId = savedSession.id.value,
        eventType = AuditEventType.GuestSessionCreated,
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
