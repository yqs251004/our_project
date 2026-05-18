package riichinexus.api.http

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpApp
import riichinexus.microservices.auth.router.AuthMicroserviceRouter
import riichinexus.microservices.club.router.ClubMicroserviceRouter
import riichinexus.microservices.dictionary.router.DictionaryMicroserviceRouter
import riichinexus.microservices.opsanalytics.router.OpsAnalyticsQueryRouter
import riichinexus.microservices.opsanalytics.router.DemoScenarioRouter
import riichinexus.microservices.opsanalytics.router.OpsAnalyticsAdminRouter
import riichinexus.microservices.platformadmin.router.PlatformAdminRouter
import riichinexus.microservices.player.router.PlayerMicroserviceRouter
import riichinexus.microservices.publicquery.router.PublicQueryRouter
import riichinexus.microservices.tournament.appeal.router.TournamentAppealRouter
import riichinexus.microservices.tournament.router.TournamentMicroserviceRouter

object ApiRouter:

  def httpApp(
      routeContext: RouteContext
  ): HttpApp[IO] =
    val support = RouteSupport(routeContext)
    (
      DocsRouter.routes(support) <+>
        ApiMessageRouter.routes(support) <+>
        DemoScenarioRouter.routes(support) <+>
        AuthMicroserviceRouter.authRoutes(support) <+>
        AuthMicroserviceRouter.publicRoutes(support) <+>
        PublicQueryRouter.routes(support) <+>
        PlayerMicroserviceRouter.routes(support) <+>
        ClubMicroserviceRouter.routes(support) <+>
        TournamentMicroserviceRouter.routes(support) <+>
        TournamentAppealRouter.routes(support) <+>
        OpsAnalyticsQueryRouter.auditRoutes(support) <+>
        OpsAnalyticsQueryRouter.dashboardRoutes(support) <+>
        DictionaryMicroserviceRouter.routes(support) <+>
        OpsAnalyticsAdminRouter.routes(support) <+>
        PlatformAdminRouter.routes(support) <+>
        HealthRouter.routes(routeContext.storageLabel)
    ).orNotFound
