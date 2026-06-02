package riichinexus.system.api.http

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpApp

object ApiRouter:

  def httpApp(routeContext: RouteContext): HttpApp[IO] =
    val support = RouteSupport.fromRouteContext(routeContext)
    (
      APIMessageRouter.routes(support) <+>
        HealthRouter.routes(RouteContext.storageLabel(routeContext))
    ).orNotFound
