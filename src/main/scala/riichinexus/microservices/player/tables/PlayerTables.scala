package riichinexus.microservices.player.tables

import riichinexus.application.ports.PlayerRepository
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.PlayerListQuery

final class PlayerTables(
    playerRepository: PlayerRepository
):
  def listPlayers(query: PlayerListQuery): Vector[Player] =
    query.clubId
      .map(playerRepository.findByClub)
      .getOrElse(playerRepository.findAll())
      .filter(player => query.clubId.forall(player.boundClubIds.contains))
      .filter(player => query.status.forall(_ == player.status))
      .sortBy(player => (player.nickname, player.id.value))

  def findPlayer(playerId: PlayerId): Option[Player] =
    playerRepository.findById(playerId)

object PlayerTables:
  val OwnedTables: Vector[String] = Vector(
    "players"
  )
