package riichinexus.microservices.player.domain

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.RoleGrant
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}

import riichinexus.system.json.JsonCodecs.given

/** 玩家账号在业务域中的持久化资料。
  *
  * 该模型把登录用户、展示昵称、段位、ELO、俱乐部归属、平台角色和封禁状态关联在一起，是身份恢复、排行榜和后台管理的基础数据。
  */
final case class Player(
    id: PlayerId,
    userId: String,
    nickname: String,
    registeredAt: Instant,
    currentRank: RankSnapshot,
    elo: Int,
    clubId: Option[ClubId] = None,
    affiliatedClubIds: Vector[ClubId] = Vector.empty,
    status: PlayerStatus = PlayerStatus.Active,
    roleGrants: Vector[RoleGrant] = Vector.empty,
    bannedReason: Option[String] = None,
    version: Int = 0
)
