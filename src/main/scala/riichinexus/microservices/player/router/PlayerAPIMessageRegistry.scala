package riichinexus.microservices.player.router
import riichinexus.api.functions.RegisteredAPIMessageFunctions

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.api.*
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse

object PlayerAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessageFunctions.created[CreatePlayerAPIMessage, PlayerProfileView],
      RegisteredAPIMessageFunctions.api[GetCurrentPlayerAPIMessage, PlayerProfileView],
      RegisteredAPIMessageFunctions.api[GetPlayerAPIMessage, PlayerProfileView],
      RegisteredAPIMessageFunctions.api[ListPlayersAPIMessage, PagedResponse[PlayerProfileView]],
      RegisteredAPIMessageFunctions.api[PublicPlayerLeaderboardAPIMessage, PagedResponse[PlayerLeaderboardEntry]]
    )
