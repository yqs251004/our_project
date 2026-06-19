package riichinexus.microservices.tournament.objects.stage.table.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.stage.table.TableSeat
import riichinexus.microservices.tournament.objects.stage.table.TableStatus
import upickle.default.{ReadWriter, macroRW}

/** TournamentTableView 表示赛事牌桌视图 的前端展示视图。 */

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
