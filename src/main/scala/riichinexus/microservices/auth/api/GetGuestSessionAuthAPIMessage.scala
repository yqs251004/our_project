package riichinexus.microservices.auth.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{GuestAccessSession, GuestSessionId}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.objects.apiTypes.GuestSessionResponse
import upickle.default.*

final case class GetGuestSessionAuthAPIMessage(
    sessionId: String
) extends APIMessage[GuestSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestAccessSession] =
    IO {
      context.support.authModule.guestSessionTable
        .find(GuestSessionId(sessionId))
        .getOrElse(throw NoSuchElementException(s"Guest session $sessionId was not found"))
    }
