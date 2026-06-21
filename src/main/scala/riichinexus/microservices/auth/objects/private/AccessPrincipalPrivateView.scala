package riichinexus.microservices.auth.objects.`private`

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** 私有认证 API 返回给其他后端模块的访问主体快照。
  *
  * 它只在服务间传递鉴权结果，携带展示名、可选玩家 ID 和角色授予记录，避免下游模块直接读取认证表。
  */
final case class AccessPrincipalPrivateView(
    principalId: String,
    displayName: String,
    playerId: Option[PlayerId],
    roleGrants: Vector[RoleGrant]
)
