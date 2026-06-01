package riichinexus.api.http.functions

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpApp
import riichinexus.api.http.RouteContext

object ApiRouterFunctions:

  def httpApp(routeContext: RouteContext): HttpApp[IO] =
    val support = RouteSupportFunctions.fromRouteContext(routeContext)
    (
      APIMessageRouterFunctions.routes(support) <+>
        HealthRouterFunctions.routes(RouteContextFunctions.storageLabel(routeContext))
    ).orNotFound
