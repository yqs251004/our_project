package riichinexus.microservices.auth.api.`private`

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.GuestSessionId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.domain.model.GuestAccessSession
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
import upickle.default.*

final case class ResolveGuestSessionAuthPrivateAPIMessage(
    sessionId: GuestSessionId
) extends APIMessage[GuestAccessSession] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestAccessSession] =
    for
      seenAt <- IO.realTimeInstant
      module = context.support.authModule
      session <- IO.blocking {
        module.transactionManager.inTransaction {
          touchGuestSession(context.connection, seenAt)
        }
      }
    yield session

  private def touchGuestSession(connection: java.sql.Connection, seenAt: Instant): GuestAccessSession =
    val session = GuestSessionTable
      .findById(connection, sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
    ensureCanAuthenticate(session, seenAt)
    GuestSessionTable.save(connection, session.touch(seenAt))

  private def ensureCanAuthenticate(session: GuestAccessSession, seenAt: Instant): Unit =
    require(session.canAuthenticate(seenAt), inactiveSessionMessage(session, seenAt))

  private def inactiveSessionMessage(session: GuestAccessSession, at: Instant): String =
    if session.isRevoked then
      s"Guest session ${session.id.value} has been revoked"
    else if session.isUpgraded then
      s"Guest session ${session.id.value} has already been upgraded to player access"
    else if session.isExpired(at) then
      s"Guest session ${session.id.value} expired at ${session.expiresAt}"
    else
      s"Guest session ${session.id.value} cannot be used for authentication"
