package riichinexus.microservices.auth.api

import java.time.Duration
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.AuthModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
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
      module = context.support.authModule
      input = resolveInput
      command = CreateGuestSessionCommand(
        input = input,
        createdAt = createdAt
      )
      session <- IO {
        module.transactionManager.inTransaction {
          createGuestSession(context.connection, module, command)
        }
      }
    yield GuestSessionResponse.fromDomain(session)

  private def createGuestSession(
      connection: java.sql.Connection,
      module: AuthModuleContext,
      command: CreateGuestSessionCommand
  ): GuestAccessSession =
    val session = GuestAccessSession.create(
      id = IdGenerator.guestSessionId(),
      createdAt = command.createdAt,
      displayName = command.input.displayName,
      ttl = command.input.ttl,
      deviceFingerprint = command.input.deviceFingerprint
    )
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitWithinTransaction(
        DomainChange(
          aggregate = session,
          persist = GuestSessionTable.save(connection, _),
          auditEntries = savedSession =>
            Vector(
              AuditEventEntry(
                id = IdGenerator.auditEventId(),
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
        )
      )

  private def resolveInput: ResolvedGuestSessionInput =
    ResolvedGuestSessionInput(
      displayName = displayName.map(_.trim).filter(_.nonEmpty).getOrElse("guest"),
      ttl = Duration.ofHours(ttlHours.getOrElse(24 * 30).toLong),
      deviceFingerprint = deviceFingerprint
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
