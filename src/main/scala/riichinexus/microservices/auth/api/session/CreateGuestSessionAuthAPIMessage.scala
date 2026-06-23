package riichinexus.microservices.auth.api.session

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import java.time.Duration
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.auth.domain.session.functions.AuthIdGenerator
import riichinexus.microservices.auth.domain.session.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.session.model.GuestAccessSession
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.session.apiTypes.GuestSessionResponse
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
      (resolvedDisplayName, resolvedTtl, resolvedDeviceFingerprint) = resolveInput
      savedSession <- IO.blocking {
        {
          createGuestSession(context.connection, resolvedDisplayName, resolvedTtl, resolvedDeviceFingerprint, createdAt)
        }
      }
      _ <- RecordAuditEventsPrivateAPIMessage(createGuestSessionAudit(savedSession, createdAt)).plan(context)
    yield guestSessionResponse(savedSession)

  private def createGuestSession(
      connection: java.sql.Connection,
      displayName: String,
      ttl: Duration,
      deviceFingerprint: Option[String],
      createdAt: Instant
  ): GuestAccessSession =
    val session = GuestAccessSessionFunctions.create(
      id = AuthIdGenerator.guestSessionId(),
      createdAt = createdAt,
      displayName = displayName,
      ttl = ttl,
      deviceFingerprint = deviceFingerprint
    )
    GuestSessionTable.save(connection, session)

  private def createGuestSessionAudit(
      savedSession: GuestAccessSession,
      createdAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.GuestSession,
        aggregateId = savedSession.id.value,
        eventType = AuditEventType.GuestSessionCreated,
        occurredAt = createdAt,
        actorId = None,
        details = Map(
          StructuredEventField.toString(StructuredEventField.ExpiresAt) -> savedSession.expiresAt.toString,
          StructuredEventField.toString(StructuredEventField.DeviceFingerprint) -> savedSession.deviceFingerprint.getOrElse("none")
        ),
        note = None
      )
    )

  private def resolveInput: (String, Duration, Option[String]) =
    ttlHours.foreach(hours => require(hours > 0, "Guest session ttlHours must be positive"))
    (
      displayName.map(_.trim).filter(_.nonEmpty).getOrElse("guest"),
      Duration.ofHours(ttlHours.getOrElse(24 * 30).toLong),
      deviceFingerprint
    )

  private def guestSessionResponse(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )

