package riichinexus.microservices.player.objects.apiTypes

import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.PlayerStatus
import upickle.default.{ReadWriter, macroRW}

/** 玩家排行榜查询的筛选和分页参数。
  *
  * 可以按俱乐部和玩家状态收窄榜单范围，排序规则由后端排行榜计算逻辑统一决定。
  */
final case class PlayerLeaderboardQuery(
    clubId: Option[ClubId] = None,
    status: Option[PlayerStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object PlayerLeaderboardQuery:
  given ReadWriter[PlayerLeaderboardQuery] = macroRW
