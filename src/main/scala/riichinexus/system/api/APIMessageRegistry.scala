package riichinexus.system.api

import riichinexus.microservices.auth.router.AuthAPIMessageRegistry
import riichinexus.microservices.club.router.ClubAPIMessageRegistry
import riichinexus.microservices.notification.router.NotificationAPIMessageRegistry
import riichinexus.microservices.opsanalytics.router.OpsAnalyticsAPIMessageRegistry
import riichinexus.microservices.player.router.PlayerAPIMessageRegistry
import riichinexus.microservices.platformadmin.router.PlatformAdminAPIMessageRegistry
import riichinexus.microservices.tournament.appeal.router.TournamentAppealAPIMessageRegistry
import riichinexus.microservices.tournament.mahjongcore.router.MahjongCoreAPIMessageRegistry
import riichinexus.microservices.tournament.router.TournamentAPIMessageRegistry

object APIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    AuthAPIMessageRegistry.apiMessages ++
      PlayerAPIMessageRegistry.apiMessages ++
      ClubAPIMessageRegistry.apiMessages ++
      NotificationAPIMessageRegistry.apiMessages ++
      PlatformAdminAPIMessageRegistry.apiMessages ++
      OpsAnalyticsAPIMessageRegistry.apiMessages ++
      TournamentAppealAPIMessageRegistry.apiMessages ++
      MahjongCoreAPIMessageRegistry.apiMessages ++
      TournamentAPIMessageRegistry.apiMessages

  val apiMessagesByName: Map[String, RegisteredAPIMessage] =
    apiMessages.map(apiMessage => normalize(apiMessage.apiName) -> apiMessage).toMap

  require(
    apiMessagesByName.size == apiMessages.size,
    "API message names must be unique"
  )

  def normalize(apiName: String): String =
    apiName.trim.toLowerCase
