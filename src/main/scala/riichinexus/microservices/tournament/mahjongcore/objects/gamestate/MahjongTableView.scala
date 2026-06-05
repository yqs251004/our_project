package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import riichinexus.microservices.tournament.mahjongcore.objects.action.{MahjongLegalAction, MahjongPublicEventView}
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 前端可见的整张实时麻将桌状态，是 MahjongTableState 按观看权限裁剪后的 API 返回体。 */
final case class MahjongTableView(
    tableId: TableId,
    status: MahjongTableStatus,
    ruleset: MahjongRuleset,
    seats: Vector[MahjongSeatView],
    currentRound: Option[MahjongRoundView],
    legalActions: Vector[MahjongLegalAction],
    finishedRoundCount: Int,
    lastEventSequenceNo: Int,
    lastEvent: Option[MahjongPublicEventView] = None,
    version: Int
)

object MahjongTableView:
  given ReadWriter[MahjongTableView] = macroRW
