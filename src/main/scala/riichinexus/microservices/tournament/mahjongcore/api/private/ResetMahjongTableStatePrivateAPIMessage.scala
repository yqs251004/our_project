package riichinexus.microservices.tournament.mahjongcore.api.`private`

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRuleset, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 供后端重置流程清空并重建实时麻将状态。 */
final case class ResetMahjongTableStatePrivateAPIMessage(
    tableId: String,
    request: ResetMahjongTableStateRequest
) extends APIMessage[MahjongTableView]:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.blocking {
      ResetMahjongTableStatePrivateAPIMessage.resetAndSave(
        context.connection,
        TableId(tableId)
      )
    }

object ResetMahjongTableStatePrivateAPIMessage:

  def resetAndSave(
      connection: java.sql.Connection,
      tableId: TableId
  ): MahjongTableView =
    val state = MahjongGameStateTransitionFunctions.notStartedTable(tableId, MahjongRuleset()).copy(version = 1)
    MahjongTableStateTable.save(connection, state)
    MahjongGameStateTransitionFunctions.toView(state, viewerPlayerId = None, includeLegalActions = false)
