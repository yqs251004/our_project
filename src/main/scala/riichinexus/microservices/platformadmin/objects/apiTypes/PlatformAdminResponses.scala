package riichinexus.microservices.platformadmin.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class PlatformAdminPlayerView(
    playerId: PlayerId,
    userId: String,
    nickname: String,
    status: PlayerStatus,
    clubIds: Vector[ClubId],
    bannedReason: Option[String],
    isSuperAdmin: Boolean
) derives CanEqual

object PlatformAdminPlayerView:
  def fromDomain(player: Player): PlatformAdminPlayerView =
    PlatformAdminPlayerView(
      playerId = player.id,
      userId = player.userId,
      nickname = player.nickname,
      status = player.status,
      clubIds = player.boundClubIds,
      bannedReason = player.bannedReason,
      isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin)
    )

final case class PlatformAdminClubView(
    clubId: ClubId,
    name: String,
    creator: PlayerId,
    createdAt: Instant,
    memberCount: Int,
    adminCount: Int,
    totalPoints: Int,
    powerRating: Double,
    dissolvedAt: Option[Instant],
    dissolvedBy: Option[PlayerId]
) derives CanEqual

object PlatformAdminClubView:
  def fromDomain(club: Club): PlatformAdminClubView =
    PlatformAdminClubView(
      clubId = club.id,
      name = club.name,
      creator = club.creator,
      createdAt = club.createdAt,
      memberCount = club.members.size,
      adminCount = club.admins.size,
      totalPoints = club.totalPoints,
      powerRating = club.powerRating,
      dissolvedAt = club.dissolvedAt,
      dissolvedBy = club.dissolvedBy
    )

type PlatformAdminPlayerResponse = PlatformAdminPlayerView
type PlatformAdminClubResponse = PlatformAdminClubView

object PlatformAdminResponses:
  given ReadWriter[PlatformAdminPlayerView] = macroRW
  given ReadWriter[PlatformAdminClubView] = macroRW
