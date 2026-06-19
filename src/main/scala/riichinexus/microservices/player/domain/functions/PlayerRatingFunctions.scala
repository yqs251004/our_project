package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.RankSnapshot

/** PlayerRatingFunctions 提供玩家评级相关的领域计算、校验和转换函数。 */

private[player] object PlayerRatingFunctions:
  def updateRank(player: Player, rank: RankSnapshot): Player =
    player.copy(currentRank = rank)

  def applyElo(player: Player, delta: Int): Player =
    player.copy(elo = player.elo + delta)
