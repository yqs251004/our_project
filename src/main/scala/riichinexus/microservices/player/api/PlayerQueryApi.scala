package riichinexus.microservices.player.api

import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.PlayerListQuery
import riichinexus.microservices.player.tables.PlayerTables

object PlayerQueryApi:

  def listPlayers(
      tables: PlayerTables,
      query: PlayerListQuery,
      containsIgnoreCase: (String, String) => Boolean
  ): Vector[Player] =
    tables.listPlayers(query)
      .filter(player => query.nickname.forall(containsIgnoreCase(player.nickname, _)))

  def findPlayer(
      tables: PlayerTables,
      playerId: PlayerId
  ): Option[Player] =
    tables.findPlayer(playerId)
