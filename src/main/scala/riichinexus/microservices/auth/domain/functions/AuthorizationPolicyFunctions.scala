package riichinexus.microservices.auth.domain.functions
import riichinexus.microservices.auth.objects.Permission

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
import riichinexus.microservices.auth.domain.{AuthorizationFailure, AuthorizationPolicy}
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.auth.objects.Role

object AuthorizationPolicyFunctions:
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
    import input.*

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
