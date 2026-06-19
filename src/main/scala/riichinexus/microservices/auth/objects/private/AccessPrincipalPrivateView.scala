package riichinexus.microservices.auth.objects.`private`

import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** AccessPrincipalPrivateView 表示后端内部使用的Access访问主体后端内部视图 read model，包含principalId、显示名、玩家 ID、roleGrants。 */

final case class AccessPrincipalPrivateView(
    principalId: String,
    displayName: String,
    playerId: Option[PlayerId],
    roleGrants: Vector[RoleGrant]
)
