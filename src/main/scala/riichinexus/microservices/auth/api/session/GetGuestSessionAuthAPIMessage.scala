package riichinexus.microservices.auth.api.session

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.auth.objects.session.GuestSessionId

import riichinexus.microservices.auth.domain.session.model.GuestAccessSession
import riichinexus.microservices.auth.objects.session.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
/** 获取游客会话详情。 */
final case class GetGuestSessionAuthAPIMessage(
    sessionId: String
) extends APIMessage[GuestSessionResponse]:

  override def plan(context: ApiPlanContext): IO[GuestSessionResponse] =
    for
      id <- IO.blocking(GuestSessionId(sessionId))
      session <- IO.blocking(findGuestSession(context, id))
    yield guestSessionResponse(session)

  private def findGuestSession(context: ApiPlanContext, sessionId: GuestSessionId) =
    GuestSessionTable
      .findById(context.connection, sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))

  private def guestSessionResponse(session: GuestAccessSession): GuestSessionResponse =
    GuestSessionResponse(
      id = session.id.value,
      displayName = session.displayName,
      createdAt = session.createdAt.toString
    )
