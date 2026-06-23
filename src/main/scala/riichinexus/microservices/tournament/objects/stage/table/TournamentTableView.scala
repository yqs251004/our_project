package riichinexus.microservices.tournament.objects.stage.table

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.stage.table.TableSeat
import riichinexus.microservices.tournament.objects.stage.table.TableStatus
import upickle.default.{ReadWriter, macroRW}

/** 前端展示赛事牌桌详情和列表时使用的牌桌视图。
  *
  * 视图包含座位、轮次、淘汰赛节点、状态时间线、牌谱/记录关联、申诉单和重置次数，是牌桌页面的主数据源。
  */
final case class TournamentTableView(
    tableId: String,
    tableNo: Int,
    tournamentId: String,
    stageId: String,
    seats: Vector[TableSeat],
    stageRoundNumber: Int,
    bracketMatchId: Option[String],
    bracketRoundNumber: Option[Int],
    status: TableStatus,
    startedAt: Option[String],
    scoringStartedAt: Option[String],
    endedAt: Option[String],
    paifuId: Option[String],
    matchRecordId: Option[String],
    appealTicketIds: Vector[String],
    resetCount: Int
)

object TournamentTableView:
  given ReadWriter[TournamentTableView] = macroRW
