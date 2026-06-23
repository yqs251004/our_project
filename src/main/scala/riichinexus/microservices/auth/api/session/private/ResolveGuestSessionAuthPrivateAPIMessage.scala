package riichinexus.microservices.auth.api.session.`private`

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.auth.objects.session.GuestSessionId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.domain.session.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.session.model.GuestAccessSession
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
/** 供后端服务解析游客会话记录。 */
final case class ResolveGuestSessionAuthPrivateAPIMessage(
    sessionId: GuestSessionId
) extends APIMessage[GuestAccessSession]:

  override def plan(context: ApiPlanContext): IO[GuestAccessSession] =
    for
      seenAt <- IO.realTimeInstant
      session <- IO.blocking {
        {
          touchGuestSession(context.connection, seenAt)
        }
      }
    yield session

  private def touchGuestSession(connection: java.sql.Connection, seenAt: Instant): GuestAccessSession =
    val session = GuestSessionTable
      .findById(connection, sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
    ensureCanAuthenticate(session, seenAt)
    GuestSessionTable.save(connection, GuestAccessSessionFunctions.touch(session, seenAt))

  private def ensureCanAuthenticate(session: GuestAccessSession, seenAt: Instant): Unit =
    require(GuestAccessSessionFunctions.canAuthenticate(session, seenAt), inactiveSessionMessage(session, seenAt))

  private def inactiveSessionMessage(session: GuestAccessSession, at: Instant): String =
    if GuestAccessSessionFunctions.isRevoked(session) then
      s"Guest session ${session.id.value} has been revoked"
    else if GuestAccessSessionFunctions.isUpgraded(session) then
      s"Guest session ${session.id.value} has already been upgraded to player access"
    else if GuestAccessSessionFunctions.isExpired(session, at) then
      s"Guest session ${session.id.value} expired at ${session.expiresAt}"
    else
      s"Guest session ${session.id.value} cannot be used for authentication"
