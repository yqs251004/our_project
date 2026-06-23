package riichinexus.microservices.club.objects.profile

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.rankprivilege.ClubRankNode
import riichinexus.microservices.club.objects.relation.ClubRelationView
import upickle.default.{ReadWriter, macroRW}

/** 后台和俱乐部管理接口使用的俱乐部完整视图。
  *
  * 与公开目录不同，这里保留成员、管理员、资金、点数池、等级树、关系和解散状态，适合需要管理权限的工作台读取。
  */
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
)

object ClubView:
  given ReadWriter[ClubView] = macroRW
