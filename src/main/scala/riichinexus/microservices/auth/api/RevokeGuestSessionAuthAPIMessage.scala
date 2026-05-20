package riichinexus.microservices.auth.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{AuditEventEntry, GuestAccessSession, GuestSessionId, IdGenerator}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import upickle.default.*

final case class RevokeGuestSessionAuthAPIMessage(
    sessionId: String,
    reason: Option[String] = None
) extends APIMessage[GuestSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestAccessSession] =
    IO {
      val module = context.support.authModule
      val revokedAt = Instant.now()
      val guestSessionId = GuestSessionId(sessionId)
      val revokeReason = reason.filter(_.trim.nonEmpty).getOrElse("revoked-by-operator")
      module.transactionManager.inTransaction {
        module.guestSessionRepository.findById(guestSessionId).map { session =>
          val updated = module.guestSessionRepository.save(
            session.revoke(revokeReason, revokedAt)
          )
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "guest-session",
              aggregateId = guestSessionId.value,
              eventType = "GuestSessionRevoked",
              occurredAt = revokedAt,
              details = Map("reason" -> updated.revokedReason.getOrElse(revokeReason))
            )
          )
          updated
        }.getOrElse(
          throw NoSuchElementException(s"Guest session $sessionId was not found")
        )
      }
    }
