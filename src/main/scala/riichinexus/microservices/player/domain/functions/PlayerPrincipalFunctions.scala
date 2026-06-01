package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.player.domain.Player

object PlayerPrincipalFunctions:
  def asPrincipal(player: Player): AccessPrincipal =
    AccessPrincipal(
      principalId = player.id.value,
      displayName = player.nickname,
      playerId = Some(player.id),
      roleGrants = PlayerRoleFunctions.effectiveRoleGrants(player)
    )
