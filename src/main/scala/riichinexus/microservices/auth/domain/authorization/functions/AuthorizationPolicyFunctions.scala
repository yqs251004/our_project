package riichinexus.microservices.auth.domain.authorization.functions
import riichinexus.microservices.auth.objects.authorization.Permission

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.domain.authorization.model.AuthorizationPolicy
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.domain.authorization.model.AccessPrincipal
import riichinexus.microservices.auth.objects.authorization.Role

/** AuthorizationPolicyFunctions 提供授权策略函数 相关的领域校验和权限判断。 */

private[auth] object AuthorizationPolicyFunctions:
  def permitAll: AuthorizationPolicy =
    AuthorizationPolicy(_ => true)

  def strict: AuthorizationPolicy =
    AuthorizationPolicy(strictCan)

  def can(
      policy: AuthorizationPolicy,
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  ): Boolean =
    policy.canEvaluate(
      AuthorizationPolicy.DecisionInput(
        principal = principal,
        permission = permission,
        clubId = clubId,
        tournamentId = tournamentId,
        subjectPlayerId = subjectPlayerId
      )
    )

  def requirePermission(
      policy: AuthorizationPolicy,
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  ): Unit =
    if !can(policy, principal, permission, clubId, tournamentId, subjectPlayerId) then
      throw AuthorizationFailure(
        s"${principal.displayName} is not allowed to perform $permission"
      )

  def strictCan(input: AuthorizationPolicy.DecisionInput): Boolean =
    val principal = input.principal
    val permission = input.permission
    val clubId = input.clubId
    val tournamentId = input.tournamentId
    val subjectPlayerId = input.subjectPlayerId

    permission match
      case Permission.ViewPublicSchedule |
          Permission.ViewClubDirectory |
          Permission.ViewPublicLeaderboard =>
        true

      case Permission.ViewOwnDashboard =>
        AccessPrincipalFunctions.isSuperAdmin(principal) || principal.playerId.exists(subjectPlayerId.contains)

      case Permission.ViewClubDashboard =>
        clubId.exists(id => AccessPrincipalFunctions.hasClubRole(principal, Role.ClubAdmin, id))

      case Permission.SubmitClubApplication =>
        AccessPrincipalFunctions.isGuest(principal) || AccessPrincipalFunctions.hasRole(principal, Role.RegisteredPlayer)

      case Permission.WithdrawClubApplication =>
        AccessPrincipalFunctions.isGuest(principal) || AccessPrincipalFunctions.hasRole(principal, Role.RegisteredPlayer)

      case Permission.ManageClubMembership |
          Permission.ManageClubOperations |
          Permission.SetClubTitle |
          Permission.AssignClubAdmin =>
        clubId.exists(id => AccessPrincipalFunctions.hasClubRole(principal, Role.ClubAdmin, id))

      case Permission.SubmitTournamentLineup =>
        clubId.exists(id => AccessPrincipalFunctions.hasClubRole(principal, Role.ClubAdmin, id))

      case Permission.ManageTournamentStages |
          Permission.ConfigureTournamentRules |
          Permission.ResetTableState |
          Permission.ResolveAppeal =>
        tournamentId.exists(id => AccessPrincipalFunctions.hasTournamentRole(principal, Role.TournamentAdmin, id))

      case Permission.ManageTableSeatState =>
        AccessPrincipalFunctions.isSuperAdmin(principal) ||
          tournamentId.exists(id => AccessPrincipalFunctions.hasTournamentRole(principal, Role.TournamentAdmin, id)) ||
          principal.playerId.exists(playerId => subjectPlayerId.contains(playerId))

      case Permission.FileAppealTicket =>
        AccessPrincipalFunctions.isSuperAdmin(principal) ||
          principal.playerId.exists(playerId => subjectPlayerId.forall(_ == playerId))

      case Permission.ManagePlatformOperations |
          Permission.BanRegisteredPlayer |
          Permission.DissolveClub |
          Permission.AssignTournamentAdmin =>
        AccessPrincipalFunctions.isSuperAdmin(principal)

      case Permission.ViewAuditTrail =>
        AccessPrincipalFunctions.isSuperAdmin(principal)
