package riichinexus.system.api.http

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{HttpRoutes, Request, Response, Status}
import org.http4s.dsl.io.*
import riichinexus.system.api.{APIMessageRegistry, ApiPlanContext, ApiSuccessStatus, RegisteredAPIMessage}
import riichinexus.system.objects.ErrorResponse

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
        RouteSupport.handled(support)(runAPIMessage(support, apiMessage, request))
      case None =>
        RouteSupport.jsonResponse(
          support,
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
      responseJson <- support.routeContext.executionContext.connectionFactory.withTransactionConnection { connection =>
        val context = ApiPlanContext(
          bearerToken = RouteSupport.bearerToken(support, request),
          connection = connection,
          realtimeEventBus = support.routeContext.realtimeEventBus
        )
        for
          _ <-
            if apiMessage.requiresBearerToken then IO.blocking(ApiPlanContext.requireBearerToken(context)).void
            else IO.unit
          responseJson <- apiMessage.planJson(bodyForDecode(body), context)
        yield responseJson
      }
      responseBody <- IO.blocking(ujson.write(responseJson, indent = 2))
      response <- RouteSupport.textResponse(support, httpStatus(apiMessage.successStatus), responseBody, "application/json; charset=utf-8")
    yield response

  private def httpStatus(status: ApiSuccessStatus): Status =
    status match
      case ApiSuccessStatus.Ok       => Status.Ok
      case ApiSuccessStatus.Created  => Status.Created
      case ApiSuccessStatus.Accepted => Status.Accepted

  private def bodyForDecode(body: String): String =
    Option(body).map(_.trim).filter(_.nonEmpty).getOrElse("{}")
