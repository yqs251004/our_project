package riichinexus.microservices.auth.domain.functions

import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView

/** AccessPrincipalPrivateViewFunctions 提供Access访问主体后端内部视图相关的领域计算、校验和转换函数。 */

private[auth] object AccessPrincipalPrivateViewFunctions:
  def toPrivateView(principal: AccessPrincipal): AccessPrincipalPrivateView =
    AccessPrincipalPrivateView(
      principalId = principal.principalId,
      displayName = principal.displayName,
      playerId = principal.playerId,
      roleGrants = principal.roleGrants
    )

  def toDomain(principal: AccessPrincipalPrivateView): AccessPrincipal =
    AccessPrincipal(
      principalId = principal.principalId,
      displayName = principal.displayName,
      playerId = principal.playerId,
      roleGrants = principal.roleGrants
    )
