package riichinexus.microservices.tournament.mahjongcore.api.gamestate.`private`
import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRuleset, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.`private`.InitializeMahjongTableStateRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 供后端比赛桌开局流程初始化实时麻将状态。 */
final case class InitializeMahjongTableStatePrivateAPIMessage(
    tableId: String,
    request: InitializeMahjongTableStateRequest
) extends APIMessage[MahjongTableView]:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    val requestedTableId = TableId(tableId)
    val ruleset = request.ruleset.getOrElse(MahjongRuleset())
    val seed = request.seed.getOrElse(s"mahjongcore:${requestedTableId.value}")

    for
      scheduledTable <- IO.blocking(
        TournamentGameTable.findById(context.connection, requestedTableId)
      )
      state =
        scheduledTable match
          case Some(table) =>
            MahjongGameStateTransitionFunctions.startTable(
              requestedTableId,
              ruleset,
              seed,
              table.seats
            )
          case None =>
            MahjongGameStateTransitionFunctions.startTable(
              requestedTableId,
              ruleset,
              seed
          )
      storedState <- IO.blocking(
        MahjongTableStateTable.save(context.connection, state)
      )
    yield
      MahjongGameStateTransitionFunctions.toView(
        storedState,
        viewerPlayerId = None,
        includeLegalActions = true
      )
