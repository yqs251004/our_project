package riichinexus.microservices.player.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.{ReadWriter, macroRW}

/** 玩家排行榜中的单行公开摘要。
  *
  * 排行展示使用 Elo、段位、标准化段位分、所属俱乐部和玩家状态，不暴露封禁原因或角色授予明细。
  */
final case class PlayerLeaderboardEntry(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshot,
    normalizedRankScore: Option[Int],
    clubIds: Vector[String],
    status: String
)

object PlayerLeaderboardEntry:
  given ReadWriter[PlayerLeaderboardEntry] = macroRW
