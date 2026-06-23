package riichinexus.microservices.player.objects.apiTypes

import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.objects.PlayerStatus

/** 后台和选择器查询玩家列表时使用的过滤参数。
  *
  * 查询支持按俱乐部、状态和昵称缩小范围，常用于赛事邀请、平台管理和成员管理中的玩家选择。
  */
final case class PlayerListQuery(
    clubId: Option[ClubId] = None,
    status: Option[PlayerStatus] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)
