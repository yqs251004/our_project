package riichinexus.microservices.auth.domain.functions

import java.time.Instant

import riichinexus.domain.model.{ClubId, TournamentId}
import riichinexus.microservices.auth.domain.model.{AccessPrincipal, GuestAccessSession, Role}

object AccessPrincipalFunctions:
  def isGuest(principal: AccessPrincipal): Boolean =
    principal.playerId.isEmpty

  def isSuperAdmin(principal: AccessPrincipal): Boolean =
    principal.roleGrants.exists(_.role == Role.SuperAdmin)

  def hasRole(principal: AccessPrincipal, role: Role): Boolean =
    isSuperAdmin(principal) || principal.roleGrants.exists(_.role == role)

  def hasClubRole(principal: AccessPrincipal, role: Role, clubId: ClubId): Boolean =
    isSuperAdmin(principal) || principal.roleGrants.exists(grant => grant.role == role && grant.clubId.contains(clubId))

  def hasTournamentRole(principal: AccessPrincipal, role: Role, tournamentId: TournamentId): Boolean =
    isSuperAdmin(principal) || principal.roleGrants.exists(grant =>
      grant.role == role && grant.tournamentId.contains(tournamentId)
    )

  def guest(session: GuestAccessSession = GuestAccessSessionFunctions.ephemeral()): AccessPrincipal =
    AccessPrincipal(
      principalId = session.id.value,
      displayName = session.displayName,
      playerId = None,
      roleGrants = Vector(RoleGrantFunctions.guest(session.createdAt))
    )

  def system: AccessPrincipal =
    AccessPrincipal(
      principalId = "system-bootstrap",
      displayName = "system",
      playerId = None,
      roleGrants = Vector(RoleGrantFunctions.superAdmin(Instant.EPOCH, None))
    )
