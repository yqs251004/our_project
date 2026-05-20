package riichinexus.microservices.player.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.api.*
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.microservices.player.objects.apiTypes.PlayerResponses.given
import riichinexus.system.objects.apiTypes.PagedResponse

object PlayerAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.created[CreatePlayerAPIMessage, PlayerResponse],
      RegisteredAPIMessage.api[GetCurrentPlayerAPIMessage, PlayerResponse],
      RegisteredAPIMessage.api[GetPlayerAPIMessage, PlayerResponse],
      RegisteredAPIMessage.api[ListPlayersAPIMessage, PagedResponse[PlayerProfileView]]
    )
