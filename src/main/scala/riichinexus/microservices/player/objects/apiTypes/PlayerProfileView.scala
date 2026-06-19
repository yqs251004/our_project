package riichinexus.microservices.player.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.{ReadWriter, macroRW}

/** PlayerProfileView 表示玩家Profile视图 的前端展示视图。 */

final case class PlayerProfileView(
    playerId: String,
    userId: String,
    nickname: String,
    registeredAt: String,
    currentRank: RankSnapshot,
    elo: Int,
    clubId: Option[String],
    affiliatedClubIds: Vector[String],
    status: String,
    roles: PlayerRoleFlagsView,
    bannedReason: Option[String]
)

object PlayerProfileView:
  given ReadWriter[PlayerProfileView] = macroRW
