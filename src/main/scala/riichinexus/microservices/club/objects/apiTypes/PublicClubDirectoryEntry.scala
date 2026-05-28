package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.ClubId
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
    relations: Vector[PublicClubRelationView]
) derives CanEqual

object PublicClubDirectoryEntry:
  given ReadWriter[PublicClubDirectoryEntry] = macroRW

  def apply(
      clubId: ClubId,
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
      strongestRivalClubId: Option[ClubId],
      strongestRivalPower: Option[Double],
      honorTitles: Vector[String],
      relations: Vector[PublicClubRelationView]
  ): PublicClubDirectoryEntry =
    PublicClubDirectoryEntry(
      clubId = clubId.value,
      name = name,
      memberCount = memberCount,
      activeMemberCount = activeMemberCount,
      adminCount = adminCount,
      powerRating = powerRating,
      totalPoints = totalPoints,
      treasuryBalance = treasuryBalance,
      pointPool = pointPool,
      allianceCount = allianceCount,
      rivalryCount = rivalryCount,
      strongestRivalClubId = strongestRivalClubId.map(_.value),
      strongestRivalPower = strongestRivalPower,
      honorTitles = honorTitles,
      relations = relations
    )
