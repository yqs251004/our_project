package riichinexus.microservices.auth.domain

import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*

final case class AuthorizationPolicy(
    canEvaluate: AuthorizationPolicy.DecisionInput => Boolean
):
  def can(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  ): Boolean =
    canEvaluate(
      AuthorizationPolicy.DecisionInput(
        principal = principal,
        permission = permission,
        clubId = clubId,
        tournamentId = tournamentId,
        subjectPlayerId = subjectPlayerId
      )
    )

  def requirePermission(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  ): Unit =
    if !can(principal, permission, clubId, tournamentId, subjectPlayerId) then
      throw AuthorizationFailure(
        s"${principal.displayName} is not allowed to perform $permission"
      )

object AuthorizationPolicy:
  final case class DecisionInput(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  )

  val permitAll: AuthorizationPolicy =
    AuthorizationPolicy(_ => true)

  val strict: AuthorizationPolicy =
    AuthorizationPolicy { input =>
      import input.*

      permission match
        case Permission.ViewPublicSchedule |
            Permission.ViewClubDirectory |
            Permission.ViewPublicLeaderboard =>
          true

        case Permission.ViewOwnDashboard =>
          principal.isSuperAdmin || principal.playerId.exists(subjectPlayerId.contains)

        case Permission.ViewClubDashboard =>
          clubId.exists(id => principal.hasClubRole(RoleKind.ClubAdmin, id))

        case Permission.SubmitClubApplication =>
          principal.isGuest || principal.hasRole(RoleKind.RegisteredPlayer)

        case Permission.WithdrawClubApplication =>
          principal.isGuest || principal.hasRole(RoleKind.RegisteredPlayer)

        case Permission.ManageClubMembership |
            Permission.ManageClubOperations |
            Permission.SetClubTitle |
            Permission.AssignClubAdmin =>
          clubId.exists(id => principal.hasClubRole(RoleKind.ClubAdmin, id))

        case Permission.SubmitTournamentLineup =>
          clubId.exists(id => principal.hasClubRole(RoleKind.ClubAdmin, id))

        case Permission.ManageTournamentStages |
            Permission.ConfigureTournamentRules |
            Permission.ResetTableState |
            Permission.ResolveAppeal =>
          tournamentId.exists(id => principal.hasTournamentRole(RoleKind.TournamentAdmin, id))

        case Permission.ManageTableSeatState =>
          principal.isSuperAdmin ||
            tournamentId.exists(id => principal.hasTournamentRole(RoleKind.TournamentAdmin, id)) ||
            principal.playerId.exists(playerId => subjectPlayerId.contains(playerId))

        case Permission.FileAppealTicket =>
          principal.isSuperAdmin ||
            principal.playerId.exists(playerId => subjectPlayerId.forall(_ == playerId))

        case Permission.ManagePlatformOperations |
            Permission.BanRegisteredPlayer |
            Permission.DissolveClub |
            Permission.AssignTournamentAdmin =>
          principal.isSuperAdmin

        case Permission.ViewAuditTrail =>
          principal.isSuperAdmin
    }
