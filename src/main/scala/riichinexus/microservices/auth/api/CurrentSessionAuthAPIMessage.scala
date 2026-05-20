package riichinexus.microservices.auth.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{GuestSessionId, PlayerId}
import riichinexus.microservices.auth.objects.apiTypes.CurrentSessionResponse
import upickle.default.*

final case class CurrentSessionAuthAPIMessage(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[CurrentSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[CurrentSessionResponse] =
    IO {
      context.support.resolveCurrentSessionView(
        operatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_)),
        guestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
      )
    }
