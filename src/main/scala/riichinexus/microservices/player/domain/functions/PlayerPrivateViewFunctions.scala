package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView

/** PlayerPrivateViewFunctions 将玩家领域模型转换为后端内部 private view。 */
private[player] object PlayerPrivateViewFunctions:
  def fromPlayer(player: Player): PlayerPrivateView =
    PlayerPrivateView(
      id = player.id,
      userId = player.userId,
      nickname = player.nickname,
      currentRank = player.currentRank,
      elo = player.elo,
      clubId = player.clubId,
      affiliatedClubIds = player.affiliatedClubIds,
      status = player.status,
      roleGrants = player.roleGrants,
      active = player.status == PlayerStatus.Active,
      bannedReason = player.bannedReason
    )
