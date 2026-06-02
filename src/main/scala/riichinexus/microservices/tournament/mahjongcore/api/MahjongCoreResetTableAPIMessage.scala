package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRuleset, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.ResetMahjongTableRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 重置 tableId 对应的实时麻将状态。 */
final case class MahjongCoreResetTableAPIMessage(
    tableId: String,
    request: ResetMahjongTableRequest
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.blocking {
      val id = TableId(tableId)
      val state = MahjongGameStateTransitionFunctions.notStartedTable(id, MahjongRuleset()).copy(version = 1)
      MahjongTableStateTable.save(context.connection, state)
      MahjongGameStateTransitionFunctions.toView(state, viewerPlayerId = None, includeLegalActions = false)
    }
