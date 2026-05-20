package riichinexus.microservices.player.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class PlayerRoleFlagsView(
    isRegisteredPlayer: Boolean,
    isClubAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isSuperAdmin: Boolean
) derives CanEqual

final case class PlayerProfileView(
    playerId: PlayerId,
    userId: String,
    nickname: String,
    registeredAt: Instant,
    currentRank: RankSnapshot,
    elo: Int,
    clubId: Option[ClubId],
    affiliatedClubIds: Vector[ClubId],
    status: PlayerStatus,
    roles: PlayerRoleFlagsView,
    bannedReason: Option[String]
) derives CanEqual

object PlayerProfileView:
  def fromDomain(player: Player): PlayerProfileView =
    PlayerProfileView(
      playerId = player.id,
      userId = player.userId,
      nickname = player.nickname,
      registeredAt = player.registeredAt,
      currentRank = player.currentRank,
      elo = player.elo,
      clubId = player.clubId,
      affiliatedClubIds = player.affiliatedClubIds,
      status = player.status,
      roles = PlayerRoleFlagsView(
        isRegisteredPlayer = player.effectiveRoleGrants.exists(_.role == RoleKind.RegisteredPlayer),
        isClubAdmin = player.roleGrants.exists(_.role == RoleKind.ClubAdmin),
        isTournamentAdmin = player.roleGrants.exists(_.role == RoleKind.TournamentAdmin),
        isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin)
      ),
      bannedReason = player.bannedReason
    )

type PlayerResponse = PlayerProfileView

object PlayerResponses:
  given ReadWriter[PlayerRoleFlagsView] = macroRW
  given ReadWriter[PlayerProfileView] = macroRW
