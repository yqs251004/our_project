package riichinexus.microservices.club.objects.audit

import upickle.default.{ReadWriter, macroRW}
import riichinexus.system.json.JsonCodecs.given

/** 公开俱乐部近期对局中单个座位的成绩摘要。
  *
  * 它保留玩家昵称、所属俱乐部、座位风、名次和分数变化，供详情页说明这场比赛中每位选手的结果。
  */
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
