package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.PlayerStatus

private[player] object PlayerStatusFunctions:
  def ban(player: Player, reason: String): Player =
    player.copy(
      status = PlayerStatus.Banned,
      bannedReason = Some(reason)
    )
