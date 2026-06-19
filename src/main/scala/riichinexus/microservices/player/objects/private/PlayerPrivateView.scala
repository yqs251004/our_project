package riichinexus.microservices.player.objects.`private`

import riichinexus.microservices.auth.objects.`private`.RoleGrant
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}
import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** PlayerPrivateView 表示后端内部使用的玩家后端内部视图 read model，包含 ID、用户 ID、昵称、currentRank、elo、俱乐部 ID等。 */

final case class PlayerPrivateView(
    id: PlayerId,
    userId: String,
    nickname: String,
    currentRank: RankSnapshot,
    elo: Int,
    clubId: Option[ClubId],
    affiliatedClubIds: Vector[ClubId],
    status: PlayerStatus,
    roleGrants: Vector[RoleGrant],
    active: Boolean,
    bannedReason: Option[String]
)
