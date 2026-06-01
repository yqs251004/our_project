package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.microservices.club.objects.relationmanagement.ClubRelationView
import upickle.default.*

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
) derives CanEqual

object PublicClubDirectoryEntry:
  given ReadWriter[PublicClubDirectoryEntry] = macroRW
