package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
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
)

object PlatformAdminClubView:
  given ReadWriter[PlatformAdminClubView] = macroRW
