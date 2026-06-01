package riichinexus.api.http.functions

import cats.effect.IO
import org.http4s.{Request, Response, Status}
import riichinexus.api.functions.ApiPlanSupportFunctions
import riichinexus.api.http.{RouteContext, RouteSupport}
import upickle.default.Writer

object RouteSupportFunctions:

  def fromRouteContext(routeContext: RouteContext): RouteSupport =
    RouteSupport(
      routeContext = routeContext,
      apiPlanSupport = ApiPlanSupportFunctions.fromExecutionContext(routeContext.executionContext)
    )

  def bearerToken(support: RouteSupport, request: Request[IO]): Option[String] =
    HttpRequestFunctions.bearerToken(request)

  def handled(support: RouteSupport)(io: => IO[Response[IO]]): IO[Response[IO]] =
    HttpResponseFunctions.handled(support.routeContext)(io)

  def textResponse(
      support: RouteSupport,
      status: Status,
      payload: String,
      contentType: String
  ): IO[Response[IO]] =
    HttpResponseFunctions.textResponse(support.routeContext, status, payload, contentType)

  def jsonResponse[T: Writer](
      support: RouteSupport,
      status: Status,
      payload: T
  ): IO[Response[IO]] =
    HttpResponseFunctions.jsonResponse(support.routeContext, status, payload)
