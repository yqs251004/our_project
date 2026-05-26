package riichinexus.microservices.player.objects

import java.time.Instant

import riichinexus.domain.model.{
  AccessPrincipal,
  ClubId,
  PlayerId,
  RoleGrant,
  RoleKind,
  TournamentId
}

final case class Player(
    id: PlayerId,
    userId: String,
    nickname: String,
    registeredAt: Instant,
    currentRank: RankSnapshot,
    elo: Int,
    clubId: Option[ClubId] = None,
    affiliatedClubIds: Vector[ClubId] = Vector.empty,
    status: PlayerStatus = PlayerStatus.Active,
    roleGrants: Vector[RoleGrant] = Vector.empty,
    bannedReason: Option[String] = None,
    version: Int = 0
) derives CanEqual:
  def boundClubIds: Vector[ClubId] =
    (clubId.toVector ++ affiliatedClubIds).distinct

  def effectiveRoleGrants: Vector[RoleGrant] =
    if roleGrants.exists(_.role == RoleKind.RegisteredPlayer) then roleGrants
    else RoleGrant.registered(registeredAt) +: roleGrants

  def asPrincipal: AccessPrincipal =
    AccessPrincipal(
      principalId = id.value,
      displayName = nickname,
      playerId = Some(id),
      roleGrants = effectiveRoleGrants
    )

  def joinClub(newClubId: ClubId): Player =
    val updatedBoundClubs = (boundClubIds :+ newClubId).distinct
    val nextPrimaryClubId = clubId.orElse(Some(newClubId))
    copy(
      clubId = nextPrimaryClubId,
      affiliatedClubIds = updatedBoundClubs.filterNot(nextPrimaryClubId.contains)
    )

  def leaveClub(existingClubId: ClubId): Player =
    val remaining = boundClubIds.filterNot(_ == existingClubId)
    copy(
      clubId = remaining.headOption,
      affiliatedClubIds = remaining.drop(1)
    )

  def leaveClub: Player =
    clubId match
      case Some(primaryClubId) => leaveClub(primaryClubId)
      case None                => copy(affiliatedClubIds = Vector.empty)

  def updateRank(rank: RankSnapshot): Player =
    copy(currentRank = rank)

  def applyElo(delta: Int): Player =
    copy(elo = elo + delta)

  def grantRole(grant: RoleGrant): Player =
    val normalized = roleGrants.filterNot(existing =>
      existing.role == grant.role &&
        existing.clubId == grant.clubId &&
        existing.tournamentId == grant.tournamentId
    )
    copy(roleGrants = (normalized :+ grant).sortBy(_.grantedAt.toEpochMilli))

  def revokeClubAdmin(clubId: ClubId): Player =
    copy(roleGrants = roleGrants.filterNot(grant =>
      grant.role == RoleKind.ClubAdmin && grant.clubId.contains(clubId)
    ))

  def revokeTournamentAdmin(tournamentId: TournamentId): Player =
    copy(roleGrants = roleGrants.filterNot(grant =>
      grant.role == RoleKind.TournamentAdmin && grant.tournamentId.contains(tournamentId)
    ))

  def ban(reason: String): Player =
    copy(
      status = PlayerStatus.Banned,
      bannedReason = Some(reason)
    )
