package riichinexus.domain.model

import java.time.Instant

final case class RoleGrant(
    role: RoleKind,
    grantedAt: Instant,
    grantedBy: Option[PlayerId] = None,
    clubId: Option[ClubId] = None,
    tournamentId: Option[TournamentId] = None
) derives CanEqual:
  require(
    role match
      case RoleKind.Guest | RoleKind.RegisteredPlayer | RoleKind.SuperAdmin =>
        clubId.isEmpty && tournamentId.isEmpty
      case RoleKind.ClubAdmin =>
        clubId.nonEmpty && tournamentId.isEmpty
      case RoleKind.TournamentAdmin =>
        tournamentId.nonEmpty && clubId.isEmpty,
    s"Invalid scope for role $role"
  )

  def appliesToClub(targetClubId: ClubId): Boolean =
    role == RoleKind.SuperAdmin || clubId.contains(targetClubId)

  def appliesToTournament(targetTournamentId: TournamentId): Boolean =
    role == RoleKind.SuperAdmin || tournamentId.contains(targetTournamentId)

object RoleGrant:
  def guest(at: Instant = Instant.now()): RoleGrant =
    RoleGrant(RoleKind.Guest, grantedAt = at)

  def registered(at: Instant): RoleGrant =
    RoleGrant(RoleKind.RegisteredPlayer, grantedAt = at)

  def clubAdmin(
      clubId: ClubId,
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = RoleKind.ClubAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy,
      clubId = Some(clubId)
    )

  def tournamentAdmin(
      tournamentId: TournamentId,
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = RoleKind.TournamentAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy,
      tournamentId = Some(tournamentId)
    )

  def superAdmin(
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = RoleKind.SuperAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy
    )
