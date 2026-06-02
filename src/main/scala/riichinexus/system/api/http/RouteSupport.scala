package riichinexus.system.api.http

import cats.effect.IO
import org.http4s.{Request, Response, Status}
import riichinexus.system.api.runtime.ApiPlanSupport
import upickle.default.Writer

final case class RouteSupport(
    routeContext: RouteContext,
    apiPlanSupport: ApiPlanSupport
)

object RouteSupport:

  def fromRouteContext(routeContext: RouteContext): RouteSupport =
    RouteSupport(
      routeContext = routeContext,
      apiPlanSupport = ApiPlanSupport.fromExecutionContext(routeContext.executionContext)
    )

  def bearerToken(support: RouteSupport, request: Request[IO]): Option[String] =
    HttpRequest.bearerToken(request)

  def handled(support: RouteSupport)(io: => IO[Response[IO]]): IO[Response[IO]] =
    HttpResponse.handled(support.routeContext)(io)

  def textResponse(
      support: RouteSupport,
      status: Status,
      payload: String,
      contentType: String
  ): IO[Response[IO]] =
    HttpResponse.textResponse(support.routeContext, status, payload, contentType)

  def jsonResponse[T: Writer](
      support: RouteSupport,
      status: Status,
      payload: T
  ): IO[Response[IO]] =
    HttpResponse.jsonResponse(support.routeContext, status, payload)
