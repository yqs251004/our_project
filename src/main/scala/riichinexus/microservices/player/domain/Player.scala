package riichinexus.microservices.player.domain

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.RoleGrant
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}

import riichinexus.system.json.JsonCodecs.given
/** Player 表示后端领域中的玩家状态或规则，包含 ID、用户 ID、昵称、registeredAt、currentRank、elo等。 */
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
)