package riichinexus.api.http.functions

import cats.effect.IO
import org.http4s.{HttpRoutes, Request, Response, Status}
import org.http4s.dsl.io.*
import riichinexus.api.functions.{APIMessageRegistryFunctions, ApiPlanContextFunctions}
import riichinexus.api.{ApiPlanContext, ApiSuccessStatus, RegisteredAPIMessage}
import riichinexus.api.http.RouteSupport
import riichinexus.system.objects.ErrorResponse

object APIMessageRouterFunctions:

  def routes(
      support: RouteSupport,
      apiMessagesByName: Map[String, RegisteredAPIMessage] = APIMessageRegistryFunctions.apiMessagesByName
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
    val normalizedApiName = APIMessageRegistryFunctions.normalize(apiName)
    apiMessagesByName.get(normalizedApiName) match
      case Some(apiMessage) =>
        RouteSupportFunctions.handled(support)(runAPIMessage(support, apiMessage, request))
      case None =>
        RouteSupportFunctions.jsonResponse(
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
      responseJson <- support.apiPlanSupport.executionContext.connectionFactory.withTransactionConnection { connection =>
        val context = ApiPlanContext(
          support = support.apiPlanSupport,
          bearerToken = RouteSupportFunctions.bearerToken(support, request),
          connection = connection
        )
        for
          _ <-
            if apiMessage.requiresBearerToken then IO.blocking(ApiPlanContextFunctions.requireBearerToken(context)).void
            else IO.unit
          responseJson <- apiMessage.planJson(bodyForDecode(body), context)
        yield responseJson
      }
      responseBody <- IO.blocking(ujson.write(responseJson, indent = 2))
      response <- RouteSupportFunctions.textResponse(support, httpStatus(apiMessage.successStatus), responseBody, "application/json; charset=utf-8")
    yield response

  private def httpStatus(status: ApiSuccessStatus): Status =
    status match
      case ApiSuccessStatus.Ok => Status.Ok
      case ApiSuccessStatus.Created => Status.Created
      case ApiSuccessStatus.Accepted => Status.Accepted

  private def bodyForDecode(body: String): String =
    Option(body).map(_.trim).filter(_.nonEmpty).getOrElse("{}")
