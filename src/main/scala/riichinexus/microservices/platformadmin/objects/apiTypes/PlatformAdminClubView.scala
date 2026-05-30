package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class PlatformAdminClubView(
    clubId: String,
    name: String,
    creator: String,
    createdAt: String,
    memberCount: Int,
    adminCount: Int,
    totalPoints: Int,
    powerRating: Double,
    dissolvedAt: Option[String],
    dissolvedBy: Option[String]
) derives CanEqual

object PlatformAdminClubView:
  given ReadWriter[PlatformAdminClubView] = macroRW

  def fromDomain(club: Club): PlatformAdminClubView =
    PlatformAdminClubView(
      clubId = club.id.value,
      name = club.name,
      creator = club.creator.value,
      createdAt = club.createdAt.toString,
      memberCount = club.members.size,
      adminCount = club.admins.size,
      totalPoints = club.totalPoints,
      powerRating = club.powerRating,
      dissolvedAt = club.dissolvedAt.map(_.toString),
      dissolvedBy = club.dissolvedBy.map(_.value)
    )
