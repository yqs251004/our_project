package routes

import cats.effect.IO
import cats.syntax.all.*
import database.ApplicationContext
import org.http4s.HttpApp

object ApiRouter:

  def httpApp(
      app: ApplicationContext,
      storageLabel: String,
      corsAllowOrigin: String = "*"
  ): HttpApp[IO] =
    val support = RouteSupport(app, storageLabel, corsAllowOrigin)
    (
      DocsRouter.routes(support) <+>
        PublicRouter.routes(support) <+>
        ClubRouter.routes(support) <+>
        TournamentRouter.routes(support) <+>
        TableRouter.routes(support) <+>
        DashboardRouter.routes(support) <+>
        DictionaryRouter.routes(support) <+>
        AdminRouter.routes(support) <+>
        HealthRouter.routes(storageLabel)
    ).orNotFound
