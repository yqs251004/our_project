package riichinexus.system.api.http

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpApp
import riichinexus.system.realtime.router.RealtimeRouter

object ApiRouter:

  def httpApp(routeContext: RouteContext): HttpApp[IO] =
    val support = RouteSupport.fromRouteContext(routeContext)
    (
      RealtimeRouter.routes(routeContext) <+>
        APIMessageRouter.routes(support) <+>
        HealthRouter.routes(RouteContext.storageLabel(routeContext))
    ).orNotFound
