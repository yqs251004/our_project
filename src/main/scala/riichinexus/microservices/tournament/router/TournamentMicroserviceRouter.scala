package riichinexus.microservices.tournament.router

import cats.effect.IO
import cats.syntax.semigroupk.*
import org.http4s.HttpRoutes
import riichinexus.api.http.RouteSupport

object TournamentMicroserviceRouter:
  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = TournamentRouterDependencies.from(support)
    TournamentTableRoutes.routes(support, deps) <+>
      TournamentQueryRoutes.routes(support, deps) <+>
      TournamentManagementRoutes.routes(support, deps) <+>
      TournamentStageRoutes.routes(support, deps)
