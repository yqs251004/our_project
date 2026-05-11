package routes

import riichinexus.bootstrap.ApplicationContext

final class RouteSupport(
    val routeContext: RouteContext
) extends HttpResponseSupport
    with TournamentViewSupport
    with AuthSupport
    with DemoScenarioSupport
    with ClubViewSupport:
  override val app: ApplicationContext = routeContext.app
  val storageLabel: String = routeContext.storageLabel

object RouteSupport:

  def apply(
      app: ApplicationContext,
      storageLabel: String,
      corsAllowOrigin: String
  ): RouteSupport =
    new RouteSupport(RouteContext(app, storageLabel, corsAllowOrigin))
