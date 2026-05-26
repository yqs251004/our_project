package riichinexus.microservices.club.domain

import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.domain.service.{AuthorizationFailure, AuthorizationService}

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
      authorizationService: AuthorizationService,
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
      delegatedPrivileges: Set[String] = Set.empty
  ): Unit =
    requireClubCapability(module.authorizationService, actor, club, permission, delegatedPrivileges)

  def requireClubCapability(
      authorizationService: AuthorizationService,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    if !hasBasePermission && !hasDelegatedPrivilege(actor, club, delegatedPrivileges) then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )

  def hasDelegatedPrivilege(
      actor: AccessPrincipal,
      club: Club,
      delegatedPrivileges: Set[String]
  ): Boolean =
    actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
        delegatedPrivileges.exists(privilege => club.hasPrivilege(playerId, privilege))
    }

  def canManageClubApplications(actor: AccessPrincipal, club: Club): Boolean =
    actor.isSuperAdmin || hasDelegatedPrivilege(actor, club, Set(ClubPrivilege.ApproveRoster)) ||
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
      authorizationService: AuthorizationService,
      actor: AccessPrincipal,
      club: Club
  ): Boolean =
    actor.isSuperAdmin ||
      authorizationService.can(actor, Permission.SubmitTournamentLineup, clubId = Some(club.id)) ||
      hasDelegatedPrivilege(actor, club, Set(ClubPrivilege.PriorityLineup))
