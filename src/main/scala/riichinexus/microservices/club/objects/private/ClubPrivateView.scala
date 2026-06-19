package riichinexus.microservices.club.objects.`private`

import java.time.Instant

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** ClubPrivateView 表示后端内部使用的俱乐部后端内部视图 read model，包含 ID、名称、creator、创建时间、成员、管理员等。 */

final case class ClubPrivateView(
    id: ClubId,
    name: String,
    creator: PlayerId,
    createdAt: Instant,
    members: Vector[PlayerId],
    admins: Vector[PlayerId],
    relations: Vector[ClubRelationPrivateView],
    totalPoints: Int,
    powerRating: Double,
    treasuryBalance: Long,
    dissolvedAt: Option[Instant],
    dissolvedBy: Option[PlayerId]
)
