package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 公共大厅俱乐部目录中的单行摘要。
  *
  * 该条目只暴露可公开比较的规模、战力、资产概况、关系数量和荣誉标题，用于列表筛选和排行榜前的快速扫描。
  */
final case class PublicClubDirectoryEntry(
    clubId: String,
    name: String,
    memberCount: Int,
    activeMemberCount: Int,
    adminCount: Int,
    powerRating: Double,
    totalPoints: Int,
    treasuryBalance: Long,
    pointPool: Int,
    allianceCount: Int,
    rivalryCount: Int,
    strongestRivalClubId: Option[String],
    strongestRivalPower: Option[Double],
    honorTitles: Vector[String],
    relations: Vector[ClubRelationView]
)

object PublicClubDirectoryEntry:
  given ReadWriter[PublicClubDirectoryEntry] = macroRW
