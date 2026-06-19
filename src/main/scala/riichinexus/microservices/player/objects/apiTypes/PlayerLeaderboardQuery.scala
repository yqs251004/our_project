package riichinexus.microservices.player.objects.apiTypes
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.PlayerStatus
import upickle.default.{ReadWriter, macroRW}

/** PlayerLeaderboardQuery 表示玩家Leaderboard查询 的列表或详情查询条件。 */

final case class PlayerLeaderboardQuery(
    clubId: Option[ClubId] = None,
    status: Option[PlayerStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object PlayerLeaderboardQuery:
  given ReadWriter[PlayerLeaderboardQuery] = macroRW
