package riichinexus.microservices.club.domain.relation.functions

import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization

/** ClubRelationAuthorizationFunctions 提供俱乐部关系授权函数 相关的领域校验和权限判断。 */

private[club] object ClubRelationAuthorizationFunctions:
  def requireDirectRelationUpdate(actor: AccessPrincipalPrivateView): Unit =
    if !actor.roleGrants.exists(_.role == Role.SuperAdmin) then
      throw AuthorizationFailure("Only super admins can directly update club relations")

  def requireRelationRequestActor(actor: AccessPrincipalPrivateView, club: Club): Unit =
    ClubAuthorization.requireClubAdmin(
      actor = actor,
      club = club,
      permission = Permission.ManageClubOperations
    )
