package riichinexus.microservices.player.domain.functions

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.auth.objects.`private`.RoleGrant
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.player.domain.Player

/** PlayerRoleFunctions 提供玩家角色相关的领域计算、校验和转换函数。 */

private[player] object PlayerRoleFunctions:
  def effectiveRoleGrants(player: Player): Vector[RoleGrant] =
    if player.roleGrants.exists(_.role == Role.RegisteredPlayer) then player.roleGrants
    else RoleGrant(Role.RegisteredPlayer, grantedAt = player.registeredAt) +: player.roleGrants

  def grantRole(player: Player, grant: RoleGrant): Player =
    val normalized = player.roleGrants.filterNot(existing =>
      existing.role == grant.role &&
        existing.clubId == grant.clubId &&
        existing.tournamentId == grant.tournamentId
    )
    player.copy(roleGrants = (normalized :+ grant).sortBy(_.grantedAt.toEpochMilli))

  def revokeClubAdmin(player: Player, clubId: ClubId): Player =
    player.copy(roleGrants = player.roleGrants.filterNot(grant =>
      grant.role == Role.ClubAdmin && grant.clubId.contains(clubId)
    ))

  def revokeTournamentAdmin(player: Player, tournamentId: TournamentId): Player =
    player.copy(roleGrants = player.roleGrants.filterNot(grant =>
      grant.role == Role.TournamentAdmin && grant.tournamentId.contains(tournamentId)
    ))
