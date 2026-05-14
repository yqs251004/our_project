package riichinexus.api.http

import riichinexus.bootstrap.*

final class RouteSupport(
    val routeContext: RouteContext
) extends HttpResponseSupport
    with AuthSupport
    with DemoScenarioSupport:
  val authModule: AuthModuleContext = routeContext.authModule
  val playerModule: PlayerModuleContext = routeContext.playerModule
  val clubModule: ClubModuleContext = routeContext.clubModule
  val dictionaryModule: DictionaryModuleContext = routeContext.dictionaryModule
  val publicQueryModule: PublicQueryModuleContext = routeContext.publicQueryModule
  val opsAnalyticsModule: OpsAnalyticsModuleContext = routeContext.opsAnalyticsModule
  val tournamentModule: TournamentModuleContext = routeContext.tournamentModule
  val platformAdminModule: PlatformAdminModuleContext = routeContext.platformAdminModule
  val tournamentAppealModule: TournamentAppealModuleContext = routeContext.tournamentAppealModule
  val storageLabel: String = routeContext.storageLabel

object RouteSupport:

  def apply(routeContext: RouteContext): RouteSupport =
    new RouteSupport(routeContext)
