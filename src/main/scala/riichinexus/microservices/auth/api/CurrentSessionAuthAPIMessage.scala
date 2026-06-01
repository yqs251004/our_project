package riichinexus.microservices.auth.api
import riichinexus.microservices.auth.api.`private`.CurrentSessionViewResolver

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{GuestSessionId, PlayerId}
import riichinexus.microservices.auth.objects.apiTypes.CurrentSessionView
import upickle.default.*

final case class CurrentSessionAuthAPIMessage(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[CurrentSessionView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[CurrentSessionView] =
    for
      input <- IO.blocking(resolveInput)
      session <- IO.blocking(
        CurrentSessionViewResolver.resolve(context, 
        operatorId = input.operatorId,
        guestSessionId = input.guestSessionId
        )
      )
    yield session

  private def resolveInput: CurrentSessionInput =
    CurrentSessionInput(
      operatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_)),
      guestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
    )

  private final case class CurrentSessionInput(
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  )
