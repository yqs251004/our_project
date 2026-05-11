package routes

import riichinexus.bootstrap.ApplicationContext

final case class RouteContext(
    app: ApplicationContext,
    storageLabel: String,
    corsAllowOrigin: String
)
