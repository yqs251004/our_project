package api

import cats.effect.IO
import database.ApplicationContext
import org.http4s.HttpApp
import org.http4s.server.middleware.Logger
import routes.ApiRouter

object ApiHttpApp:

  def build(
      app: ApplicationContext,
      storageLabel: String,
      corsAllowOrigin: String = "*",
      logHeaders: Boolean = true,
      logBody: Boolean = false
  ): HttpApp[IO] =
    Logger.httpApp(logHeaders = logHeaders, logBody = logBody)(
      ApiRouter.httpApp(app, storageLabel, corsAllowOrigin)
    )
