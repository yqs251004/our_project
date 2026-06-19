package riichinexus.system.api.http

import cats.effect.IO
import org.http4s.{HttpRoutes, Method, Status}

object CorsPreflightRouter:

  def routes(routeContext: RouteContext): HttpRoutes[IO] =
    val support = RouteSupport.fromRouteContext(routeContext)
    HttpRoutes.of[IO] {
      case request if request.method == Method.OPTIONS && request.uri.path.renderString.startsWith("/api/") =>
        RouteSupport.textResponse(support, Status.NoContent, "", "text/plain; charset=utf-8")
    }
