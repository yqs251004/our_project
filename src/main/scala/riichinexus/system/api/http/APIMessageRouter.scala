package riichinexus.system.api.http

import cats.effect.IO
import org.http4s.{HttpRoutes, Method, Request, Response, Status}
import riichinexus.system.api.{APIMessageRegistry, ApiPlanContext, ApiPostCommitHooks, ApiSuccessStatus, RegisteredAPIMessage}
import riichinexus.system.objects.ErrorResponse

object APIMessageRouter:

  def routes(
      support: RouteSupport,
      apiMessagesByName: Map[String, RegisteredAPIMessage] = APIMessageRegistry.apiMessagesByName
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if request.method == Method.POST =>
        apiPathName(request) match
          case Some(apiName) => dispatch(support, apiMessagesByName, apiName, request)
          case None =>
            RouteSupport.jsonResponse(
              support,
              Status.NotFound,
              ErrorResponse(
                message = s"Unknown API path: ${request.uri.path.renderString}",
                code = "api_not_found"
              )
            )
    }

  private def apiPathName(request: Request[IO]): Option[String] =
    normalizedPathSegments(request) match
      case Vector("api", apiName) => Some(apiName)
      case _ => None

  private def normalizedPathSegments(request: Request[IO]): Vector[String] =
    request.uri.path.renderString.split('/').toVector.filter(_.nonEmpty)

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
    val postCommitHooks = ApiPostCommitHooks()
    for
      body <- request.bodyText.compile.string
      responseJson <- support.routeContext.executionContext.connectionFactory.withTransactionConnection { connection =>
        val context = ApiPlanContext(
          bearerToken = RouteSupport.bearerToken(support, request),
          connection = connection,
          realtimeEventBus = support.routeContext.realtimeEventBus,
          postCommitHooks = Some(postCommitHooks)
        )
        for
          _ <-
            if apiMessage.requiresBearerToken then IO.blocking(ApiPlanContext.requireBearerToken(context)).map(_ => ())
            else IO.unit
          responseJson <- apiMessage.planJson(bodyForDecode(body), context)
        yield responseJson
      }
      _ <- runPostCommitHooks(postCommitHooks.drain)
      responseBody <- IO.blocking(ujson.write(responseJson, indent = 2))
      response <- RouteSupport.textResponse(support, httpStatus(apiMessage.successStatus), responseBody, "application/json; charset=utf-8")
    yield response

  private def runPostCommitHooks(hooks: Vector[IO[Unit]]): IO[Unit] =
    hooks.foldLeft(IO.unit) { (acc, hook) =>
      acc.flatMap(_ => hook.handleErrorWith(_ => IO.unit))
    }

  private def httpStatus(status: ApiSuccessStatus): Status =
    status match
      case ApiSuccessStatus.Ok       => Status.Ok
      case ApiSuccessStatus.Created  => Status.Created
      case ApiSuccessStatus.Accepted => Status.Accepted

  private def bodyForDecode(body: String): String =
    Option(body).map(_.trim).filter(_.nonEmpty).getOrElse("{}")
