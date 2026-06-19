package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** PublicClubDirectoryEntry 表示前后端共享的公开俱乐部目录条目 数据结构。 */

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
