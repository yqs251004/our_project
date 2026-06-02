package riichinexus.microservices.player.router
import riichinexus.system.api.RegisteredAPIMessage

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.api.*
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse

object PlayerAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.created[CreatePlayerAPIMessage, PlayerProfileView],
      RegisteredAPIMessage.api[GetCurrentPlayerAPIMessage, PlayerProfileView],
      RegisteredAPIMessage.api[GetPlayerAPIMessage, PlayerProfileView],
      RegisteredAPIMessage.api[ListPlayersAPIMessage, PagedResponse[PlayerProfileView]],
      RegisteredAPIMessage.api[PublicPlayerLeaderboardAPIMessage, PagedResponse[PlayerLeaderboardEntry]]
    )
