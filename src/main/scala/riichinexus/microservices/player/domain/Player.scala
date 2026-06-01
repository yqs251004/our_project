package riichinexus.microservices.player.domain

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId, TournamentId}
import riichinexus.microservices.auth.domain.model.{AccessPrincipal, RoleGrant}
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}

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
) derives CanEqual
