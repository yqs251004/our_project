package riichinexus.system.api.http

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*

object CorsPreflightRouter:

  def routes(routeContext: RouteContext): HttpRoutes[IO] =
    val support = RouteSupport.fromRouteContext(routeContext)
    HttpRoutes.of[IO] {
      case OPTIONS -> Root / "api" / _ =>
        RouteSupport.textResponse(support, NoContent, "", "text/plain; charset=utf-8")
    }
