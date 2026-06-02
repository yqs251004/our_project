package riichinexus.microservices.club.domain
import riichinexus.microservices.auth.objects.Permission

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
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
      actor: AccessPrincipal,
      club: Club,
      permission: Permission
  ): Unit =
    requireClubAdmin(AuthorizationPolicyFunctions.strict, actor, club, permission)

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
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[ClubPrivilegeCode] = Set.empty
  ): Unit =
    requireClubCapability(AuthorizationPolicyFunctions.strict, actor, club, permission, delegatedPrivileges)

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
      actor: AccessPrincipal,
      club: Club
  ): Boolean =
    canManageClubTournamentParticipation(AuthorizationPolicyFunctions.strict, actor, club)

  def canManageClubTournamentParticipation(
      authorizationService: AuthorizationPolicy,
      actor: AccessPrincipal,
      club: Club
  ): Boolean =
    AccessPrincipalFunctions.isSuperAdmin(actor) ||
      AuthorizationPolicyFunctions.can(authorizationService, actor, Permission.SubmitTournamentLineup, clubId = Some(club.id)) ||
      hasDelegatedPrivilege(actor, club, Set(ClubPrivilegeCode.PriorityLineup))
