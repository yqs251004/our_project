package riichinexus.api.http

import riichinexus.api.runtime.ApiPlanSupport
import cats.effect.IO
import org.http4s.{Request, Response, Status}
import upickle.default.Writer

final class RouteSupport(
    val routeContext: RouteContext
):
  val apiPlanSupport: ApiPlanSupport = ApiPlanSupport(routeContext.executionContext)
  val storageLabel: String = routeContext.storageLabel

  def bearerToken(request: Request[IO]): Option[String] =
    HttpRequestSupport.bearerToken(request)

  def handled(io: => IO[Response[IO]]): IO[Response[IO]] =
    HttpResponseSupport.handled(routeContext)(io)

  def textResponse(status: Status, payload: String, contentType: String): IO[Response[IO]] =
    HttpResponseSupport.textResponse(routeContext, status, payload, contentType)

  def jsonResponse[T: Writer](status: Status, payload: T): IO[Response[IO]] =
    HttpResponseSupport.jsonResponse(routeContext, status, payload)

  def optionJsonResponse[T: Writer](value: Option[T], statusIfSome: Status = Status.Ok): IO[Response[IO]] =
    HttpResponseSupport.optionJsonResponse(routeContext, value, statusIfSome)

  def emptyResponse(status: Status): IO[Response[IO]] =
    HttpResponseSupport.emptyResponse(routeContext, status)

  def openApiJson(request: Request[IO]): String =
    HttpOpenApiSupport.openApiJson(request)

  def pagedJsonResponse[T: Writer](
      request: Request[IO],
      items: Vector[T],
      appliedFilters: Map[String, String] = Map.empty,
      defaultLimit: Int = 20,
      maxLimit: Int = 100
  ): IO[Response[IO]] =
    HttpPaginationSupport.pagedJsonResponse(routeContext, request, items, appliedFilters, defaultLimit, maxLimit)

object RouteSupport:

  def apply(routeContext: RouteContext): RouteSupport =
    new RouteSupport(routeContext)
