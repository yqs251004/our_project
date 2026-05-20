package riichinexus.microservices.auth.api

import java.time.Duration
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{AuditEventEntry, GuestAccessSession, IdGenerator}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import upickle.default.*

final case class CreateGuestSessionAuthAPIMessage(
    displayName: Option[String] = None,
    ttlHours: Option[Int] = None,
    deviceFingerprint: Option[String] = None
) extends APIMessage[GuestSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestAccessSession] =
    IO {
      val module = context.support.authModule
      val createdAt = Instant.now()
      module.transactionManager.inTransaction {
        val normalizedDisplayName =
          displayName.map(_.trim).filter(_.nonEmpty).getOrElse("guest")

        val savedSession = module.guestSessionRepository.save(
          GuestAccessSession.create(
            id = IdGenerator.guestSessionId(),
            createdAt = createdAt,
            displayName = normalizedDisplayName,
            ttl = Duration.ofHours(ttlHours.getOrElse(24 * 30).toLong),
            deviceFingerprint = deviceFingerprint
          )
        )
        module.auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "guest-session",
            aggregateId = savedSession.id.value,
            eventType = "GuestSessionCreated",
            occurredAt = createdAt,
            details = Map(
              "expiresAt" -> savedSession.expiresAt.toString,
              "deviceFingerprint" -> savedSession.deviceFingerprint.getOrElse("none")
            )
          )
        )
        savedSession
      }
    }
