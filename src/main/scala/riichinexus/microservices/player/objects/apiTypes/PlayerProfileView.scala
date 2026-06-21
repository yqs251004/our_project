package riichinexus.microservices.player.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.{ReadWriter, macroRW}

/** 玩家个人页和会话恢复使用的完整资料视图。
  *
  * 它聚合账号身份、昵称、注册时间、段位、Elo、俱乐部归属、角色标记和封禁原因，前端可据此渲染个人入口和状态提示。
  */
final case class PlayerProfileView(
    playerId: String,
    userId: String,
    nickname: String,
    registeredAt: String,
    currentRank: RankSnapshot,
    elo: Int,
    clubId: Option[String],
    affiliatedClubIds: Vector[String],
    status: String,
    roles: PlayerRoleFlagsView,
    bannedReason: Option[String]
)

object PlayerProfileView:
  given ReadWriter[PlayerProfileView] = macroRW
