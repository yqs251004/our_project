package riichinexus.api.http

import cats.effect.IO
import org.http4s.Request
import org.http4s.headers
import riichinexus.api.docs.OpenApiSupport

trait HttpOpenApiSupport:

  def baseUrl(request: Request[IO]): String =
    request.headers.get[headers.Host] match
      case Some(host) =>
        s"http://${host.host}${host.port.map(port => s":$port").getOrElse("")}"
      case None =>
        "http://127.0.0.1"

  def openApiJson(request: Request[IO]): String =
    OpenApiSupport.openApiJson(baseUrl(request))
