package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** PublicClubLeaderboardQuery 表示公开俱乐部Leaderboard查询 的列表或详情查询条件。 */

final case class PublicClubLeaderboardQuery(
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object PublicClubLeaderboardQuery:
  given ReadWriter[PublicClubLeaderboardQuery] = macroRW
