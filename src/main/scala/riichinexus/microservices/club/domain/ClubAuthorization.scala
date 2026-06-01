package riichinexus.microservices.club.domain

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.auth.domain.{AuthorizationFailure, AuthorizationPolicy}

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
      module: ClubModuleContext,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission
  ): Unit =
    requireClubAdmin(module.authorizationService, actor, club, permission)

  def requireClubAdmin(
      authorizationService: AuthorizationPolicy,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission
  ): Unit =
    requireClubCapability(
      authorizationService = authorizationService,
      actor = actor,
      club = club,
      permission = permission,
      delegatedPrivileges = Set.empty
    )

  def requireClubCapability(
      module: ClubModuleContext,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[ClubPrivilegeCode] = Set.empty
  ): Unit =
    requireClubCapability(module.authorizationService, actor, club, permission, delegatedPrivileges)

  def requireClubCapability(
      authorizationService: AuthorizationPolicy,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[ClubPrivilegeCode]
  ): Unit =
    val hasBasePermission = AuthorizationPolicyFunctions.can(authorizationService, actor, permission, clubId = Some(club.id))
    if !hasBasePermission && !hasDelegatedPrivilege(actor, club, delegatedPrivileges) then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )

  def hasDelegatedPrivilege(
      actor: AccessPrincipal,
      club: Club,
      delegatedPrivileges: Set[ClubPrivilegeCode]
  ): Boolean =
    actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
        delegatedPrivileges.exists(privilege => ClubFunctions.hasPrivilege(club, playerId, privilege))
    }

  def canManageClubApplications(actor: AccessPrincipal, club: Club): Boolean =
    AccessPrincipalFunctions.isSuperAdmin(actor) || hasDelegatedPrivilege(actor, club, Set(ClubPrivilegeCode.ApproveRoster)) ||
      actor.playerId.exists(club.admins.contains)

  def requireClubApplicationManager(actor: AccessPrincipal, club: Club): Unit =
    if !canManageClubApplications(actor, club) then
      throw AuthorizationFailure(s"${actor.displayName} cannot manage membership applications for club ${club.id.value}")

  def canManageClubTournamentParticipation(
      module: ClubModuleContext,
      actor: AccessPrincipal,
      club: Club
  ): Boolean =
    canManageClubTournamentParticipation(module.authorizationService, actor, club)

  def canManageClubTournamentParticipation(
      authorizationService: AuthorizationPolicy,
      actor: AccessPrincipal,
      club: Club
  ): Boolean =
    AccessPrincipalFunctions.isSuperAdmin(actor) ||
      AuthorizationPolicyFunctions.can(authorizationService, actor, Permission.SubmitTournamentLineup, clubId = Some(club.id)) ||
      hasDelegatedPrivilege(actor, club, Set(ClubPrivilegeCode.PriorityLineup))
