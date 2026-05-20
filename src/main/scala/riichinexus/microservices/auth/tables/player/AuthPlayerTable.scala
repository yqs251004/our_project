package riichinexus.microservices.auth.tables.player

import riichinexus.application.ports.PlayerRepository
import riichinexus.domain.model.{Player, PlayerId}

final class AuthPlayerTable(
    playerRepository: PlayerRepository
):
  def find(playerId: PlayerId): Option[Player] =
    playerRepository.findById(playerId)
