package riichinexus.microservices.auth.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.{GuestSessionId, PlayerId}
import riichinexus.microservices.auth.objects.apiTypes.{CurrentSessionResponse, CurrentSessionView}
import upickle.default.*

final case class CurrentSessionAuthAPIMessage(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[CurrentSessionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[CurrentSessionResponse] =
    for
      input <- IO.blocking(resolveInput)
      session <- IO.blocking(
        context.resolveCurrentSessionView(
        operatorId = input.operatorId,
        guestSessionId = input.guestSessionId
        )
      )
    yield currentSessionResponse(session)

  private def resolveInput: CurrentSessionInput =
    CurrentSessionInput(
      operatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_)),
      guestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
    )

  private def currentSessionResponse(view: CurrentSessionView): CurrentSessionResponse =
    CurrentSessionResponse(
      principalKind = view.principalKind,
      principalId = view.principalId,
      displayName = view.displayName,
      authenticated = view.authenticated,
      roles = view.roles,
      player = view.player,
      guestSession = view.guestSession
    )

  private final case class CurrentSessionInput(
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  )
