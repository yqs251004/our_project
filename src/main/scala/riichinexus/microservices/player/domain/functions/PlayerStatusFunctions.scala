package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.PlayerStatus

/** PlayerStatusFunctions 提供玩家状态相关的领域计算、校验和转换函数。 */

private[player] object PlayerStatusFunctions:
  def ban(player: Player, reason: String): Player =
    player.copy(
      status = PlayerStatus.Banned,
      bannedReason = Some(reason)
    )
