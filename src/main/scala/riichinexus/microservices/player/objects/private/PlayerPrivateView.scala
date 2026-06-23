package riichinexus.microservices.player.objects.`private`

import riichinexus.microservices.auth.objects.authorization.`private`.RoleGrant
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}
import riichinexus.microservices.player.objects.PlayerId

/** 后端服务间共享的玩家完整读模型。
  *
  * 它包含玩家档案、账号 ID、段位、Elo、俱乐部归属、角色授予和封禁状态，供赛事、俱乐部、认证和平台管理共同解析玩家身份。
  */
final case class PlayerPrivateView(
    id: PlayerId,
    userId: String,
    nickname: String,
    currentRank: RankSnapshot,
    elo: Int,
    clubId: Option[ClubId],
    affiliatedClubIds: Vector[ClubId],
    status: PlayerStatus,
    roleGrants: Vector[RoleGrant],
    active: Boolean,
    bannedReason: Option[String]
)
