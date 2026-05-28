package riichinexus.api.http

import java.util.NoSuchElementException

import cats.effect.IO
import org.http4s.*
import org.typelevel.ci.CIString
import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.service.AuthenticationFailure
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.system.objects.ErrorResponse
import upickle.default.*

object HttpResponseSupport:

  private def defaultHeaders(routeContext: RouteContext) = List(
    Header.Raw(CIString("Access-Control-Allow-Origin"), routeContext.corsAllowOrigin),
    Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS"),
    Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type, Authorization"),
    Header.Raw(CIString("Access-Control-Max-Age"), "600")
  )

  private def withDefaultHeaders(routeContext: RouteContext, response: Response[IO]): Response[IO] =
    response.withHeaders(Headers(response.headers.headers ++ defaultHeaders(routeContext)))

  def handled(routeContext: RouteContext)(io: => IO[Response[IO]]): IO[Response[IO]] =
    IO.defer(io).handleErrorWith(errorResponse(routeContext))

  def errorResponse(routeContext: RouteContext)(error: Throwable): IO[Response[IO]] =
    error match
      case handled: OptimisticConcurrencyException =>
        jsonResponse(
          routeContext,
          Status.Conflict,
          ErrorResponse(
            message = handled.getMessage,
            code = "optimistic_concurrency_conflict",
            details = Map(
              "aggregateType" -> handled.aggregateType,
              "aggregateId" -> handled.aggregateId,
              "expectedVersion" -> handled.expectedVersion.toString
            ) ++ handled.actualVersion.map(version => "actualVersion" -> version.toString)
          )
        )
      case handled: AuthorizationFailure =>
        jsonResponse(routeContext, Status.Forbidden, ErrorResponse(handled.getMessage, code = "authorization_failed"))
      case handled: AuthenticationFailure =>
        jsonResponse(routeContext, Status.Unauthorized, ErrorResponse(handled.getMessage, code = handled.code))
      case handled: IllegalArgumentException =>
        jsonResponse(routeContext, Status.BadRequest, ErrorResponse(handled.getMessage, code = "invalid_request"))
      case handled: NoSuchElementException =>
        jsonResponse(routeContext, Status.NotFound, ErrorResponse(handled.getMessage, code = "not_found"))
      case handled: ujson.ParseException =>
        jsonResponse(routeContext, Status.BadRequest, ErrorResponse(s"Invalid JSON body: ${handled.getMessage}", code = "invalid_json"))
      case handled =>
        jsonResponse(
          routeContext,
          Status.InternalServerError,
          ErrorResponse(Option(handled.getMessage).getOrElse("Internal server error"))
        )

  def textResponse(routeContext: RouteContext, status: Status, payload: String, contentType: String): IO[Response[IO]] =
    IO.pure(
      withDefaultHeaders(
        routeContext,
        Response[IO](status = status)
          .withEntity(payload)
          .putHeaders(Header.Raw(CIString("Content-Type"), contentType))
      )
    )

  def jsonResponse[T: Writer](routeContext: RouteContext, status: Status, payload: T): IO[Response[IO]] =
    IO.blocking(write(payload, indent = 2))
      .flatMap(textResponse(routeContext, status, _, "application/json; charset=utf-8"))

  def optionJsonResponse[T: Writer](routeContext: RouteContext, value: Option[T], statusIfSome: Status = Status.Ok): IO[Response[IO]] =
    value match
      case Some(actual) => jsonResponse(routeContext, statusIfSome, actual)
      case None => jsonResponse(routeContext, Status.NotFound, ErrorResponse("Resource not found", code = "not_found"))

  def emptyResponse(routeContext: RouteContext, status: Status): IO[Response[IO]] =
    IO.pure(withDefaultHeaders(routeContext, Response[IO](status = status)))
