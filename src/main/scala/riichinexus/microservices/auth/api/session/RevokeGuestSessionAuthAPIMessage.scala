package riichinexus.microservices.auth.api.session

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.audit.api.`private`.RecordAuditEventsPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.auth.objects.session.GuestSessionId
import riichinexus.microservices.auth.domain.session.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.session.model.GuestAccessSession
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.session.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
/** 撤销游客访问会话。 */
final case class RevokeGuestSessionAuthAPIMessage(
    sessionId: String,
    reason: Option[String] = None
) extends APIMessage[GuestSessionResponse]:

  override def plan(context: ApiPlanContext): IO[GuestSessionResponse] =
    for
      revokedAt <- IO.realTimeInstant
      (resolvedSessionId, resolvedReason) = resolveInput
      updated <- IO.blocking {
        {
          revokeGuestSession(context.connection, resolvedSessionId, resolvedReason, revokedAt)
        }
      }
      _ <- RecordAuditEventsPrivateAPIMessage(revokeGuestSessionAudit(updated, resolvedReason, revokedAt)).plan(context)
    yield guestSessionResponse(updated)

  private def revokeGuestSession(
      connection: java.sql.Connection,
      sessionId: GuestSessionId,
      reason: String,
      revokedAt: Instant
  ): GuestAccessSession =
    val session = GuestSessionTable.findById(connection, sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
    GuestSessionTable.save(
      connection,
      GuestAccessSessionFunctions.revoke(session, reason, revokedAt)
    )

  private def revokeGuestSessionAudit(
      updated: GuestAccessSession,
      reason: String,
      revokedAt: Instant
  ): Vector[AuditEventDraft] =
    Vector(
      AuditEventDraft(
        aggregateType = AggregateType.GuestSession,
        aggregateId = updated.id.value,
        eventType = AuditEventType.GuestSessionRevoked,
        occurredAt = revokedAt,
        actorId = None,
        details = Map(StructuredEventField.toString(StructuredEventField.Reason) -> updated.revokedReason.getOrElse(reason)),
        note = None
      )
    )

  private def resolveInput: (GuestSessionId, String) =
    (GuestSessionId(sessionId), reason.filter(_.trim.nonEmpty).getOrElse("revoked-by-operator"))

  private def guestSessionResponse(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )

