package riichinexus.microservices.auth.domain.functions

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId
import riichinexus.microservices.auth.objects.`private`.RoleGrant
import riichinexus.microservices.auth.objects.Role

/** RoleGrantFunctions 提供角色Grant相关的领域计算、校验和转换函数。 */

private[auth] object RoleGrantFunctions:
  def appliesToClub(grant: RoleGrant, targetClubId: ClubId): Boolean =
    grant.role == Role.SuperAdmin || grant.clubId.contains(targetClubId)

  def appliesToTournament(grant: RoleGrant, targetTournamentId: TournamentId): Boolean =
    grant.role == Role.SuperAdmin || grant.tournamentId.contains(targetTournamentId)

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

  def validate(grant: RoleGrant): Unit =
    require(
      grant.role match
        case Role.Guest | Role.RegisteredPlayer | Role.SuperAdmin =>
          grant.clubId.isEmpty && grant.tournamentId.isEmpty
        case Role.ClubAdmin =>
          grant.clubId.nonEmpty && grant.tournamentId.isEmpty
        case Role.TournamentAdmin =>
          grant.tournamentId.nonEmpty && grant.clubId.isEmpty,
      s"Invalid scope for role ${Role.toString(grant.role)}"
    )
