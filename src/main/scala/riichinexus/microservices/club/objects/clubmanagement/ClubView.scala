package riichinexus.microservices.club.objects.clubmanagement

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubRankNode
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView
import upickle.default.ReadWriter

/** ClubView 表示俱乐部详情视图，包含成员、管理员、资产、等级树、关系和解散状态。 */

final case class ClubView(
    id: String,
    name: String,
    members: Vector[String],
    admins: Vector[String],
    powerRating: Double,
    treasuryBalance: Long,
    totalPoints: Int,
    pointPool: Int,
    rankTree: Vector[ClubRankNode],
    relations: Vector[ClubRelationView],
    dissolvedAt: Option[String]
) derives ReadWriter
