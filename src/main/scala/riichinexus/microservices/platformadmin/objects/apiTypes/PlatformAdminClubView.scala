package riichinexus.microservices.platformadmin.objects.apiTypes

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
