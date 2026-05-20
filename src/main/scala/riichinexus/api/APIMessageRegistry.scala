package riichinexus.api

import riichinexus.microservices.auth.router.AuthAPIMessageRegistry
import riichinexus.microservices.club.router.ClubAPIMessageRegistry
import riichinexus.microservices.dictionary.router.DictionaryAPIMessageRegistry
import riichinexus.microservices.opsanalytics.router.OpsAnalyticsAPIMessageRegistry
import riichinexus.microservices.player.router.PlayerAPIMessageRegistry
import riichinexus.microservices.platformadmin.router.PlatformAdminAPIMessageRegistry
import riichinexus.microservices.publicquery.router.PublicQueryAPIMessageRegistry
import riichinexus.microservices.tournament.router.TournamentAPIMessageRegistry
import riichinexus.microservices.tournament.appeal.router.TournamentAppealAPIMessageRegistry

object APIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    AuthAPIMessageRegistry.apiMessages ++
      PlayerAPIMessageRegistry.apiMessages ++
      PublicQueryAPIMessageRegistry.apiMessages ++
      ClubAPIMessageRegistry.apiMessages ++
      PlatformAdminAPIMessageRegistry.apiMessages ++
      DictionaryAPIMessageRegistry.apiMessages ++
      OpsAnalyticsAPIMessageRegistry.apiMessages ++
      TournamentAppealAPIMessageRegistry.apiMessages ++
      TournamentAPIMessageRegistry.apiMessages

  val apiMessagesByName: Map[String, RegisteredAPIMessage] =
    apiMessages.map(apiMessage => normalize(apiMessage.apiName) -> apiMessage).toMap

  require(
    apiMessagesByName.size == apiMessages.size,
    "API message names must be unique"
  )

  def normalize(apiName: String): String =
    apiName.trim.toLowerCase
