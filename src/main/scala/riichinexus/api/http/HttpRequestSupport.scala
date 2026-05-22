package riichinexus.api.http

import scala.util.Try

import cats.effect.IO
import org.http4s.Request
import org.typelevel.ci.CIString
import upickle.default.*

trait HttpRequestSupport:
  final case class PageQuery(limit: Int, offset: Int)

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

  def readJsonBody[T: Reader](request: Request[IO]): IO[T] =
    request.bodyText.compile.string.map { body =>
      if body.trim.isEmpty then throw IllegalArgumentException("Request body is required")
      else read[T](body)
    }

  def readOptionalJsonBody[T: Reader](request: Request[IO]): IO[Option[T]] =
    request.bodyText.compile.string.map(body => Option(body.trim).filter(_.nonEmpty).map(read[T](_)))
