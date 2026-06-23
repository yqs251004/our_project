package riichinexus.microservices.tournament.mahjongcore.api.gamestate.`private`
import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRuleset, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.`private`.ResetMahjongTableStateRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 供后端重置流程清空并重建实时麻将状态。 */
final case class ResetMahjongTableStatePrivateAPIMessage(
    tableId: String,
    request: ResetMahjongTableStateRequest
) extends APIMessage[MahjongTableView]:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    val requestedTableId = TableId(tableId)
    val state =
      MahjongGameStateTransitionFunctions
        .notStartedTable(requestedTableId, MahjongRuleset())
        .copy(version = 1)

    for
      storedState <- IO.blocking(
        MahjongTableStateTable.save(context.connection, state)
      )
    yield
      MahjongGameStateTransitionFunctions.toView(
        storedState,
        viewerPlayerId = None,
        includeLegalActions = false
      )
