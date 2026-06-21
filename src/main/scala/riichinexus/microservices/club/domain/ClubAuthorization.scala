package riichinexus.microservices.club.domain
import riichinexus.microservices.auth.objects.{Permission, Role}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView

import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.system.api.AuthorizationFailure

/** 俱乐部相关操作的领域授权辅助方法。
  *
  * 它集中处理俱乐部是否解散、操作者是否为成员/管理员、是否拥有委派权限等判断，让管理成员、审核申请和提交阵容的权限语义保持一致。
  */
object ClubAuthorization:
  def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )

  def requireClubAdmin(
      actor: AccessPrincipalPrivateView,
      club: Club,
      permission: Permission
  ): Unit =
    requireClubCapability(
      actor = actor,
      club = club,
      permission = permission,
      delegatedPrivileges = Set.empty
    )

  def requireClubCapability(
      actor: AccessPrincipalPrivateView,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[ClubPrivilegeCode] = Set.empty
  ): Unit =
    val hasBasePermission = canByClubPermission(actor, club, permission)
    if !hasBasePermission && !hasDelegatedPrivilege(actor, club, delegatedPrivileges) then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )

  def hasDelegatedPrivilege(
      actor: AccessPrincipalPrivateView,
      club: Club,
      delegatedPrivileges: Set[ClubPrivilegeCode]
  ): Boolean =
    actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
        delegatedPrivileges.exists(privilege => ClubFunctions.hasPrivilege(club, playerId, privilege))
    }

  def canManageClubApplications(actor: AccessPrincipalPrivateView, club: Club): Boolean =
    isSuperAdmin(actor) || hasDelegatedPrivilege(actor, club, Set(ClubPrivilegeCode.ApproveRoster)) ||
      actor.playerId.exists(club.admins.contains)

  def requireClubApplicationManager(actor: AccessPrincipalPrivateView, club: Club): Unit =
    if !canManageClubApplications(actor, club) then
      throw AuthorizationFailure(s"${actor.displayName} cannot manage membership applications for club ${club.id.value}")

  def canManageClubTournamentParticipation(
      actor: AccessPrincipalPrivateView,
      club: Club
  ): Boolean =
    isSuperAdmin(actor) ||
      canByClubPermission(actor, club, Permission.SubmitTournamentLineup) ||
      hasDelegatedPrivilege(actor, club, Set(ClubPrivilegeCode.PriorityLineup))

  private def canByClubPermission(actor: AccessPrincipalPrivateView, club: Club, permission: Permission): Boolean =
    isSuperAdmin(actor) ||
      (permission match
        case Permission.ViewClubDashboard |
            Permission.ManageClubMembership |
            Permission.ManageClubOperations |
            Permission.SetClubTitle |
            Permission.AssignClubAdmin |
            Permission.SubmitTournamentLineup =>
          hasClubAdminRole(actor, club)
        case _ =>
          false
      )

  private def hasClubAdminRole(actor: AccessPrincipalPrivateView, club: Club): Boolean =
    actor.roleGrants.exists(grant => grant.role == Role.ClubAdmin && grant.clubId.contains(club.id))

  private def isSuperAdmin(actor: AccessPrincipalPrivateView): Boolean =
    actor.roleGrants.exists(_.role == Role.SuperAdmin)
