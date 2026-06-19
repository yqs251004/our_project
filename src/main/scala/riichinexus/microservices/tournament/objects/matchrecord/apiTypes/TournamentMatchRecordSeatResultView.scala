package riichinexus.microservices.tournament.objects.matchrecord.apiTypes

import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecordSeatResult
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
  def fromDomain(result: MatchRecordSeatResult): TournamentMatchRecordSeatResultView =
    TournamentMatchRecordSeatResultView(
      playerId = result.playerId.value,
      seat = result.seat.toString,
      clubId = result.clubId.map(_.value),
      finalPoints = result.finalPoints,
      placement = result.placement,
      scoreDelta = result.scoreDelta,
      uma = result.uma,
      oka = result.oka
    )

  given ReadWriter[TournamentMatchRecordSeatResultView] = macroRW

