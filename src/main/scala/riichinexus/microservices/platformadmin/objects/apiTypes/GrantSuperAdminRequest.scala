package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 平台管理员授予另一个玩家超级管理员角色的请求体。
  *
  * 目标玩家通常由路径确定，`operatorId` 记录发起授权的人，供角色授予和审计链路使用。
  */
final case class GrantSuperAdminRequest(
    operatorId: PlayerId
) derives ReadWriter
