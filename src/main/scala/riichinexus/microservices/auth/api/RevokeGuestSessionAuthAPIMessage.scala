package riichinexus.microservices.auth.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.AuthModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
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
      module = context.support.authModule
      input = resolveInput
      command = RevokeGuestSessionCommand(
        input = input,
        revokedAt = revokedAt
      )
      session <- IO.blocking {
        module.transactionManager.inTransaction {
          revokeGuestSession(context.connection, module, command)
        }
      }
    yield guestSessionResponse(session)

  private def revokeGuestSession(
      connection: java.sql.Connection,
      module: AuthModuleContext,
      command: RevokeGuestSessionCommand
  ): GuestAccessSession =
    val session = GuestSessionTable.findById(connection, command.input.sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${command.input.sessionId.value} was not found"))
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitWithinTransaction(
        DomainChange(
          aggregate = GuestAccessSessionFunctions.revoke(session, command.input.reason, command.revokedAt),
          persist = GuestSessionTable.save(connection, _),
          auditEntries = updated =>
            Vector(
              AuditEventEntry(
                id = IdGenerator.auditEventId(),
                aggregateType = "guest-session",
                aggregateId = updated.id.value,
                eventType = "GuestSessionRevoked",
                occurredAt = command.revokedAt,
                actorId = None,
                details = Map("reason" -> updated.revokedReason.getOrElse(command.input.reason)),
                note = None
              )
            )
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
