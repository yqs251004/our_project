package riichinexus.microservices.auth.domain.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.auth.objects.`private`.RoleGrant

import riichinexus.system.json.JsonCodecs.given
/** AccessPrincipal 表示后端领域中的Access访问主体 状态，包含principalId、显示名、玩家 ID、roleGrants。 */
final case class AccessPrincipal(
    principalId: String,
    displayName: String,
    playerId: Option[PlayerId],
    roleGrants: Vector[RoleGrant]
)