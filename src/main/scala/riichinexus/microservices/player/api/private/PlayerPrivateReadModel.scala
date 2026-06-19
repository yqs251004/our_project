package riichinexus.microservices.player.api.`private`

import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView

/** PlayerPrivateReadModel 供后端服务执行玩家后端内部已读Model 流程，避免其它微服务直接访问内部表或领域模型。 */

private[player] object PlayerPrivateReadModel:
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
