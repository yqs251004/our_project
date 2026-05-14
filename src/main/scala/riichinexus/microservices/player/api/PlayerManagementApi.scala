package riichinexus.microservices.player.api

import riichinexus.domain.model.Player
import riichinexus.microservices.player.api.requests.CreatePlayerRequest

object PlayerManagementApi:

  def createPlayer(
      service: PlayerApplicationService,
      request: CreatePlayerRequest
  ): Player =
    service.registerPlayer(
      userId = request.userId,
      nickname = request.nickname,
      rank = request.toRankSnapshot,
      initialElo = request.initialElo
    )
