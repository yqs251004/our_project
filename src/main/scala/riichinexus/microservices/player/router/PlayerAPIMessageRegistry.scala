package riichinexus.microservices.player.router
import riichinexus.system.api.RegisteredAPIMessage


import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage, PublicPlayerLeaderboardAPIMessage}
import riichinexus.microservices.player.objects.apiTypes.{PlayerLeaderboardEntry, PlayerProfileView}
import riichinexus.system.objects.PagedResponse

object PlayerAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.created[CreatePlayerAPIMessage, PlayerProfileView],
      RegisteredAPIMessage.api[GetPlayerAPIMessage, PlayerProfileView],
      RegisteredAPIMessage.api[ListPlayersAPIMessage, PagedResponse[PlayerProfileView]],
      RegisteredAPIMessage.api[PublicPlayerLeaderboardAPIMessage, PagedResponse[PlayerLeaderboardEntry]]
    )
