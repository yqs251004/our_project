package riichinexus.microservices.auth.domain.functions

import java.time.Instant

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.domain.model.{AccessPrincipal, GuestAccessSession}
import riichinexus.microservices.auth.objects.Role

/** AccessPrincipalFunctions 提供Access访问主体相关的领域计算、校验和转换函数。 */

private[auth] object AccessPrincipalFunctions:
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
