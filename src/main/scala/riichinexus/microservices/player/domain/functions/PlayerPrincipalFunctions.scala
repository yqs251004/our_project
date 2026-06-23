package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.player.domain.Player

/** PlayerPrincipalFunctions 提供玩家访问主体相关的领域计算、校验和转换函数。 */

private[player] object PlayerPrincipalFunctions:
  def asPrincipal(player: Player): AccessPrincipalPrivateView =
    AccessPrincipalPrivateView(
      principalId = player.id.value,
      displayName = player.nickname,
      playerId = Some(player.id),
      roleGrants = PlayerRoleFunctions.effectiveRoleGrants(player)
    )
