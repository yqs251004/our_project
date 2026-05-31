package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId, TournamentId}
import riichinexus.microservices.auth.objects.Role

final case class AccessPrincipal(
    principalId: String,
    displayName: String,
    playerId: Option[PlayerId],
    roleGrants: Vector[RoleGrant]
) derives CanEqual:
  def isGuest: Boolean =
    playerId.isEmpty

  def isSuperAdmin: Boolean =
    roleGrants.exists(_.role == Role.SuperAdmin)

  def hasRole(role: Role): Boolean =
    isSuperAdmin || roleGrants.exists(_.role == role)

  def hasClubRole(role: Role, clubId: ClubId): Boolean =
    isSuperAdmin || roleGrants.exists(grant => grant.role == role && grant.clubId.contains(clubId))

  def hasTournamentRole(role: Role, tournamentId: TournamentId): Boolean =
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
