package riichinexus.microservices.tournament.domain.stage.model

import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.objects.PlayerId

/** 瑞士轮排桌时用于聚合同俱乐部或单人玩家的内部 key。
  *
  * 该类型只服务后端排桌算法，避免把 `club:*`、`player:*` 这类展示/序列化格式混进领域计算。
  */
private[tournament] enum SeatingGroupKey:
  case Club(clubId: ClubId)
  case Player(playerId: PlayerId)

private[tournament] object SeatingGroupKey:
  def sortKey(key: SeatingGroupKey): (Int, String) =
    key match
      case SeatingGroupKey.Club(clubId)     => (0, clubId.value)
      case SeatingGroupKey.Player(playerId) => (1, playerId.value)
