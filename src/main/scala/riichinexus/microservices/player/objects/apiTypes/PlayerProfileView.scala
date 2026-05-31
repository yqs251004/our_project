package riichinexus.microservices.player.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.RankSnapshotView
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
