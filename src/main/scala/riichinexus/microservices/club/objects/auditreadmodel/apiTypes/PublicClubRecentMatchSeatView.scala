package riichinexus.microservices.club.objects.auditreadmodel.apiTypes

import upickle.default.{ReadWriter, macroRW}
import riichinexus.system.json.JsonCodecs.given

/** PublicClubRecentMatchSeatView 表示公开俱乐部近期对局中的单个座位成绩，包含玩家、俱乐部归属、座位、名次、分数变化和最终点数。 */

final case class PublicClubRecentMatchSeatView(
    playerId: String,
    nickname: String,
    clubId: Option[String],
    seat: String,
    placement: Int,
    scoreDelta: Int,
    finalPoints: Int
)

object PublicClubRecentMatchSeatView:
  given ReadWriter[PublicClubRecentMatchSeatView] = macroRW
