package riichinexus.microservices.auth.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.GuestSessionId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
import upickle.default.*

final case class GetGuestSessionAuthAPIMessage(
    sessionId: String
) extends APIMessage[GuestSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestSessionResponse] =
    for
      id <- IO.blocking(GuestSessionId(sessionId))
      session <- IO.blocking(findGuestSession(context, id))
    yield GuestSessionResponse.fromDomain(session)

  private def findGuestSession(context: ApiPlanContext, sessionId: GuestSessionId) =
    GuestSessionTable
      .findById(context.connection, sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
