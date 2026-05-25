package riichinexus.microservices.auth.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.GuestSessionId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import upickle.default.*

final case class GetGuestSessionAuthAPIMessage(
    sessionId: String
) extends APIMessage[GuestSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestSessionResponse] =
    for
      id <- IO(GuestSessionId(sessionId))
      session <- IO(findGuestSession(context, id))
    yield GuestSessionResponse.fromDomain(session)

  private def findGuestSession(context: ApiPlanContext, sessionId: GuestSessionId) =
    context.support.authModule.guestSessionTable
      .find(sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
