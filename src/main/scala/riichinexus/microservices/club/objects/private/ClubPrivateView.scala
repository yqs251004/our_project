package riichinexus.microservices.club.objects.`private`

import java.time.Instant

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** 服务间使用的俱乐部完整内部快照。
  *
  * 它保留创建者、成员、管理员、关系、资产和解散信息，供权限、赛事邀请、审计和读模型刷新等后端流程共享。
  */
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
