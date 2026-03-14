package riichinexus.domain.service

import riichinexus.domain.model.*

final case class AuthorizationFailure(message: String) extends RuntimeException(message)

trait AuthorizationService:
  def can(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  ): Boolean

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

object NoOpAuthorizationService extends AuthorizationService:
  override def can(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId],
      tournamentId: Option[TournamentId],
      subjectPlayerId: Option[PlayerId]
  ): Boolean =
    true

final class StrictRbacAuthorizationService extends AuthorizationService:
  override def can(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId],
      tournamentId: Option[TournamentId],
      subjectPlayerId: Option[PlayerId]
  ): Boolean =
    permission match
      case Permission.ViewPublicSchedule |
          Permission.ViewClubDirectory |
          Permission.ViewPublicLeaderboard =>
        true

      case Permission.ViewOwnDashboard =>
        principal.isSuperAdmin || principal.playerId.exists(subjectPlayerId.contains)

      case Permission.SubmitClubApplication =>
        principal.isGuest || principal.hasRole(RoleKind.RegisteredPlayer)

      case Permission.ManageClubMembership |
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

      case Permission.FileAppealTicket =>
        principal.isSuperAdmin ||
          principal.playerId.exists(playerId => subjectPlayerId.forall(_ == playerId))

      case Permission.ManageGlobalDictionary |
          Permission.BanRegisteredPlayer |
          Permission.DissolveClub |
          Permission.AssignTournamentAdmin =>
        principal.isSuperAdmin
