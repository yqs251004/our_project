package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId, TournamentId}
import riichinexus.microservices.auth.objects.Role

final case class RoleGrant(
    role: Role,
    grantedAt: Instant,
    grantedBy: Option[PlayerId] = None,
    clubId: Option[ClubId] = None,
    tournamentId: Option[TournamentId] = None
) derives CanEqual:
  require(
    role match
      case Role.Guest | Role.RegisteredPlayer | Role.SuperAdmin =>
        clubId.isEmpty && tournamentId.isEmpty
      case Role.ClubAdmin =>
        clubId.nonEmpty && tournamentId.isEmpty
      case Role.TournamentAdmin =>
        tournamentId.nonEmpty && clubId.isEmpty,
    s"Invalid scope for role $role"
  )

  def appliesToClub(targetClubId: ClubId): Boolean =
    role == Role.SuperAdmin || clubId.contains(targetClubId)

  def appliesToTournament(targetTournamentId: TournamentId): Boolean =
    role == Role.SuperAdmin || tournamentId.contains(targetTournamentId)

object RoleGrant:
  def guest(at: Instant = Instant.now()): RoleGrant =
    RoleGrant(Role.Guest, grantedAt = at)

  def registered(at: Instant): RoleGrant =
    RoleGrant(Role.RegisteredPlayer, grantedAt = at)

  def clubAdmin(
      clubId: ClubId,
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = Role.ClubAdmin,
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
      role = Role.TournamentAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy,
      tournamentId = Some(tournamentId)
    )

  def superAdmin(
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = Role.SuperAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy
    )
