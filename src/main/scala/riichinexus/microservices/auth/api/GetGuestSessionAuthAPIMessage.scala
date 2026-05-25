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
    IO {
      context.support.authModule.guestSessionTable
        .find(GuestSessionId(sessionId))
        .map(GuestSessionResponse.fromDomain)
        .getOrElse(throw NoSuchElementException(s"Guest session $sessionId was not found"))
    }
