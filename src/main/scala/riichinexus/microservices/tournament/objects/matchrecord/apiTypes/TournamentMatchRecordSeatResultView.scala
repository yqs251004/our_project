package riichinexus.microservices.tournament.objects.matchrecord.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** TournamentMatchRecordSeatResultView 表示赛事对局记录座位结果视图 的前端展示视图。 */

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
