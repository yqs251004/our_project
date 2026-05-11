package routes

import java.util.NoSuchElementException

import scala.util.Try

import cats.effect.IO
import objects.{ErrorResponse, PagedResponse}
import org.http4s.*
import org.typelevel.ci.CIString
import riichinexus.api.OpenApiSupport
import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.service.{AuthenticationFailure, AuthorizationFailure}
import upickle.default.*

trait HttpResponseSupport:
  protected def routeContext: RouteContext

  final case class PageQuery(limit: Int, offset: Int)

  private def defaultHeaders = List(
    Header.Raw(CIString("Access-Control-Allow-Origin"), routeContext.corsAllowOrigin),
    Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS"),
    Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type, Authorization"),
    Header.Raw(CIString("Access-Control-Max-Age"), "600")
  )

  private def withDefaultHeaders(response: Response[IO]): Response[IO] =
    response.withHeaders(Headers(response.headers.headers ++ defaultHeaders))

  def handled(io: => IO[Response[IO]]): IO[Response[IO]] =
    IO.defer(io).handleErrorWith(errorResponse)

  def errorResponse(error: Throwable): IO[Response[IO]] =
    error match
      case handled: OptimisticConcurrencyException =>
        jsonResponse(
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
        jsonResponse(Status.Forbidden, ErrorResponse(handled.getMessage, code = "authorization_failed"))
      case handled: AuthenticationFailure =>
        jsonResponse(Status.Unauthorized, ErrorResponse(handled.getMessage, code = handled.code))
      case handled: IllegalArgumentException =>
        jsonResponse(Status.BadRequest, ErrorResponse(handled.getMessage, code = "invalid_request"))
      case handled: NoSuchElementException =>
        jsonResponse(Status.NotFound, ErrorResponse(handled.getMessage, code = "not_found"))
      case handled: ujson.ParseException =>
        jsonResponse(Status.BadRequest, ErrorResponse(s"Invalid JSON body: ${handled.getMessage}", code = "invalid_json"))
      case handled =>
        jsonResponse(
          Status.InternalServerError,
          ErrorResponse(Option(handled.getMessage).getOrElse("Internal server error"))
        )

  def textResponse(status: Status, payload: String, contentType: String): IO[Response[IO]] =
    IO.pure(
      withDefaultHeaders(
        Response[IO](status = status)
          .withEntity(payload)
          .putHeaders(Header.Raw(CIString("Content-Type"), contentType))
      )
    )

  def jsonResponse[T: Writer](status: Status, payload: T): IO[Response[IO]] =
    textResponse(status, write(payload, indent = 2), "application/json; charset=utf-8")

  def optionJsonResponse[T: Writer](value: Option[T], statusIfSome: Status = Status.Ok): IO[Response[IO]] =
    value match
      case Some(actual) => jsonResponse(statusIfSome, actual)
      case None => jsonResponse(Status.NotFound, ErrorResponse("Resource not found", code = "not_found"))

  def emptyResponse(status: Status): IO[Response[IO]] =
    IO.pure(withDefaultHeaders(Response[IO](status = status)))

  def baseUrl(request: Request[IO]): String =
    request.headers.get[headers.Host] match
      case Some(host) =>
        s"http://${host.host}${host.port.map(port => s":$port").getOrElse("")}"
      case None =>
        "http://127.0.0.1"

  def openApiJson(request: Request[IO]): String =
    OpenApiSupport.openApiJson(baseUrl(request))

  def parseEnum[E](label: String, value: String)(parse: String => E): E =
    Try(parse(value)).getOrElse(throw IllegalArgumentException(s"Invalid $label: $value"))

  def containsIgnoreCase(value: String, fragment: String): Boolean =
    value.toLowerCase.contains(fragment.toLowerCase)

  def queryParam(request: Request[IO], key: String): Option[String] =
    request.params.get(key)

  def bearerToken(request: Request[IO]): Option[String] =
    request.headers.headers
      .find(_.name == CIString("Authorization"))
      .map(_.value)
      .flatMap { rawValue =>
        val prefix = "Bearer "
        Option.when(rawValue.regionMatches(true, 0, prefix, 0, prefix.length))(
          rawValue.substring(prefix.length).trim
        ).filter(_.nonEmpty)
      }

  def queryIntParam(request: Request[IO], key: String): Option[Int] =
    queryParam(request, key).filter(_.nonEmpty).map { value =>
      Try(value.toInt).getOrElse(throw IllegalArgumentException(s"Query parameter $key must be an integer"))
    }

  def queryBooleanParam(request: Request[IO], key: String): Option[Boolean] =
    queryParam(request, key).filter(_.nonEmpty).map {
      case value if value.equalsIgnoreCase("true") => true
      case value if value.equalsIgnoreCase("false") => false
      case _ => throw IllegalArgumentException(s"Query parameter $key must be true or false")
    }

  def activeFilters(request: Request[IO], keys: String*): Map[String, String] =
    keys.flatMap(key => queryParam(request, key).filter(_.nonEmpty).map(key -> _)).toMap

  def pageQuery(request: Request[IO], defaultLimit: Int = 20, maxLimit: Int = 100): PageQuery =
    val limit = queryIntParam(request, "limit").getOrElse(defaultLimit)
    val offset = queryIntParam(request, "offset").getOrElse(0)
    require(limit > 0, "Query parameter limit must be positive")
    require(offset >= 0, "Query parameter offset must be non-negative")
    PageQuery(limit = math.min(limit, maxLimit), offset = offset)

  def pagedJsonResponse[T: Writer](
      request: Request[IO],
      items: Vector[T],
      appliedFilters: Map[String, String] = Map.empty,
      defaultLimit: Int = 20,
      maxLimit: Int = 100
  ): IO[Response[IO]] =
    val query = pageQuery(request, defaultLimit, maxLimit)
    val pagedItems = items.slice(query.offset, query.offset + query.limit)
    jsonResponse(
      Status.Ok,
      PagedResponse(
        items = pagedItems,
        total = items.size,
        limit = query.limit,
        offset = query.offset,
        hasMore = query.offset + pagedItems.size < items.size,
        appliedFilters = appliedFilters
      )
    )

  def readJsonBody[T: Reader](request: Request[IO]): IO[T] =
    request.bodyText.compile.string.map { body =>
      if body.trim.isEmpty then throw IllegalArgumentException("Request body is required")
      else read[T](body)
    }

  def readOptionalJsonBody[T: Reader](request: Request[IO]): IO[Option[T]] =
    request.bodyText.compile.string.map(body => Option(body.trim).filter(_.nonEmpty).map(read[T](_)))
