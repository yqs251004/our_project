package riichinexus.microservices.tournament.objects.matchrecord

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 对局记录中单个座位的公开成绩结果。
  *
  * 结果包含玩家、座位风、俱乐部归属、最终点、名次、分差、uma 和 oka，供战绩表与结算计算复用。
  */
final case class TournamentMatchRecordSeatResultView(
    playerId: String,
    seat: String,
    clubId: Option[String],
    finalPoints: Int,
    placement: Int,
    scoreDelta: Int,
    uma: Double,
    oka: Double
)

object TournamentMatchRecordSeatResultView:
  given ReadWriter[TournamentMatchRecordSeatResultView] = macroRW
