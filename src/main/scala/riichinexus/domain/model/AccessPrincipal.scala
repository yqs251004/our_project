package riichinexus.domain.model

import java.time.Instant

final case class AccessPrincipal(
    principalId: String,
    displayName: String,
    playerId: Option[PlayerId],
    roleGrants: Vector[RoleGrant]
) derives CanEqual:
  def isGuest: Boolean =
    playerId.isEmpty

  def isSuperAdmin: Boolean =
    roleGrants.exists(_.role == RoleKind.SuperAdmin)

  def hasRole(role: RoleKind): Boolean =
    isSuperAdmin || roleGrants.exists(_.role == role)

  def hasClubRole(role: RoleKind, clubId: ClubId): Boolean =
    isSuperAdmin || roleGrants.exists(grant => grant.role == role && grant.clubId.contains(clubId))

  def hasTournamentRole(role: RoleKind, tournamentId: TournamentId): Boolean =
    isSuperAdmin || roleGrants.exists(grant =>
      grant.role == role && grant.tournamentId.contains(tournamentId)
    )

object AccessPrincipal:
  def guest(session: GuestAccessSession = GuestAccessSession.ephemeral()): AccessPrincipal =
    AccessPrincipal(
      principalId = session.id.value,
      displayName = session.displayName,
      playerId = None,
      roleGrants = Vector(RoleGrant.guest(session.createdAt))
    )

  def system: AccessPrincipal =
    AccessPrincipal(
      principalId = "system-bootstrap",
      displayName = "system",
      playerId = None,
      roleGrants = Vector(RoleGrant.superAdmin(Instant.EPOCH, None))
    )
