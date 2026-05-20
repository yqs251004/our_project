package riichinexus.api.http

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpApp
import riichinexus.api.APIMessageRouter

object ApiRouter:

  def httpApp(
      routeContext: RouteContext
  ): HttpApp[IO] =
    val support = RouteSupport(routeContext)
    (
        DocsRouter.routes(support) <+>
        APIMessageRouter.routes(support) <+>
        HealthRouter.routes(routeContext.storageLabel)
    ).orNotFound
