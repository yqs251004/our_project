package riichinexus.microservices.auth.domain.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.auth.objects.`private`.RoleGrant

import riichinexus.system.json.JsonCodecs.given

/** 鉴权流程在领域层使用的访问主体。
  *
  * 它把请求中的登录身份、可选玩家身份和已授予角色集中成一个对象，供权限策略判断当前调用者能否执行某个动作。
  */
final case class AccessPrincipal(
    principalId: String,
    displayName: String,
    playerId: Option[PlayerId],
    roleGrants: Vector[RoleGrant]
)
