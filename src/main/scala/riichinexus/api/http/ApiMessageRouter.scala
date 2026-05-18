package riichinexus.api.http

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.{Request, Response, Status}
import org.http4s.dsl.io.*
import riichinexus.microservices.shared.api.responses.ErrorResponse

object ApiMessageRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "api-message-registry.json" =>
        support.handled(support.jsonResponse(Status.Ok, ApiMessageRegistry.contracts))

      case GET -> Root / "api" / "api-message-registry.json" =>
        support.handled(support.jsonResponse(Status.Ok, ApiMessageRegistry.contracts))

      case req @ POST -> Root / messageName if isApiMessage(messageName) =>
        dispatch(support, messageName, req)

      case req @ POST -> Root / "api" / messageName if isApiMessage(messageName) =>
        dispatch(support, messageName, req)
    }

  private def isApiMessage(messageName: String): Boolean =
    messageName.endsWith("ApiMessage")

  private def dispatch(
      support: RouteSupport,
      messageName: String,
      req: Request[IO]
  ): IO[Response[IO]] =
    ApiMessageRegistry.handlers.get(messageName) match
      case Some(handler) =>
        support.handled(handler.handle(support, req))
      case None =>
        support.jsonResponse(
          Status.NotFound,
          ErrorResponse(
            message = s"Unknown API message: $messageName",
            code = "api_message_not_found"
          )
        )
