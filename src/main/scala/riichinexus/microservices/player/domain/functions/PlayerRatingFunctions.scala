package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.RankSnapshot

private[player] object PlayerRatingFunctions:
  def updateRank(player: Player, rank: RankSnapshot): Player =
    player.copy(currentRank = rank)

  def applyElo(player: Player, delta: Int): Player =
    player.copy(elo = player.elo + delta)
