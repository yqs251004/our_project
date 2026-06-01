package riichinexus.api.http.functions

import cats.effect.IO
import org.http4s.Request
import org.typelevel.ci.CIString

object HttpRequestFunctions:

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
