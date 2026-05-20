package riichinexus.api

import cats.effect.IO
import org.http4s.{HttpRoutes, Request, Response, Status}
import org.http4s.dsl.io.*
import riichinexus.api.http.RouteSupport
import riichinexus.system.objects.apiTypes.ErrorResponse

object APIMessageRouter:

  def routes(
      support: RouteSupport,
      apiMessagesByName: Map[String, RegisteredAPIMessage] = APIMessageRegistry.apiMessagesByName
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "api" / apiName =>
        dispatch(support, apiMessagesByName, apiName, req)
    }

  private def dispatch(
      support: RouteSupport,
      apiMessagesByName: Map[String, RegisteredAPIMessage],
      apiName: String,
      request: Request[IO]
  ): IO[Response[IO]] =
    val normalizedApiName = APIMessageRegistry.normalize(apiName)
    apiMessagesByName.get(normalizedApiName) match
      case Some(apiMessage) =>
        support.handled(runAPIMessage(support, apiMessage, request))
      case None =>
        support.jsonResponse(
          Status.NotFound,
          ErrorResponse(
            message = s"Unknown API: $apiName",
            code = "api_not_found"
          )
        )

  private def runAPIMessage(
      support: RouteSupport,
      apiMessage: RegisteredAPIMessage,
      request: Request[IO]
  ): IO[Response[IO]] =
    for
      body <- request.bodyText.compile.string
      context = ApiPlanContext(
        support = support,
        bearerToken = support.bearerToken(request)
      )
      _ <-
        if apiMessage.requiresBearerToken then IO(context.requireBearerToken).void
        else IO.unit
      responseJson <- apiMessage.planJson(bodyForDecode(body), context)
      response <- support.textResponse(apiMessage.successStatus, ujson.write(responseJson, indent = 2), "application/json; charset=utf-8")
    yield response

  private def bodyForDecode(body: String): String =
    Option(body).map(_.trim).filter(_.nonEmpty).getOrElse("{}")
