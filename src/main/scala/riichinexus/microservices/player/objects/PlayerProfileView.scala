package riichinexus.microservices.player.objects

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.objects.Player
import riichinexus.microservices.tournament.objects.RankSnapshotView
import upickle.default.*

final case class PlayerProfileView(
    playerId: String,
    userId: String,
    nickname: String,
    registeredAt: String,
    currentRank: RankSnapshotView,
    elo: Int,
    clubId: Option[String],
    affiliatedClubIds: Vector[String],
    status: String,
    roles: PlayerRoleFlagsView,
    bannedReason: Option[String]
) derives CanEqual

object PlayerProfileView:
  given ReadWriter[PlayerProfileView] = macroRW

  def fromDomain(player: Player): PlayerProfileView =
    PlayerProfileView(
      playerId = player.id.value,
      userId = player.userId,
      nickname = player.nickname,
      registeredAt = player.registeredAt.toString,
      currentRank = RankSnapshotView.fromDomain(player.currentRank),
      elo = player.elo,
      clubId = player.clubId.map(_.value),
      affiliatedClubIds = player.affiliatedClubIds.map(_.value),
      status = player.status.toString,
      roles = PlayerRoleFlagsView(
        isRegisteredPlayer = player.effectiveRoleGrants.exists(_.role == RoleKind.RegisteredPlayer),
        isClubAdmin = player.roleGrants.exists(_.role == RoleKind.ClubAdmin),
        isTournamentAdmin = player.roleGrants.exists(_.role == RoleKind.TournamentAdmin),
        isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin)
      ),
      bannedReason = player.bannedReason
    )
