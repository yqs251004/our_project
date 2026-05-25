package riichinexus.microservices.platformadmin.objects.apiTypes

import riichinexus.domain.model.*
import upickle.default.*

final case class PlatformAdminPlayerView(
    playerId: String,
    userId: String,
    nickname: String,
    status: String,
    clubIds: Vector[String],
    bannedReason: Option[String],
    isSuperAdmin: Boolean
) derives CanEqual

object PlatformAdminPlayerView:
  def fromDomain(player: Player): PlatformAdminPlayerView =
    PlatformAdminPlayerView(
      playerId = player.id.value,
      userId = player.userId,
      nickname = player.nickname,
      status = player.status.toString,
      clubIds = player.boundClubIds.map(_.value),
      bannedReason = player.bannedReason,
      isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin)
    )

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

type PlatformAdminPlayerResponse = PlatformAdminPlayerView
type PlatformAdminClubResponse = PlatformAdminClubView

object PlatformAdminResponses:
  given ReadWriter[PlatformAdminPlayerView] = macroRW
  given ReadWriter[PlatformAdminClubView] = macroRW
