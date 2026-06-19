package riichinexus.microservices.tournament.mahjongcore.api.`private`

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRuleset, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}

import upickle.default.ReadWriter

/** 供后端比赛桌开局流程初始化实时麻将状态。 */
final case class InitializeMahjongTableStatePrivateAPIMessage(
    tableId: String,
    request: InitializeMahjongTableStateRequest
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.blocking {
      InitializeMahjongTableStatePrivateAPIMessage.initializeAndSave(
        context.connection,
        TableId(tableId),
        request
      )
    }

object InitializeMahjongTableStatePrivateAPIMessage:

  def initializeAndSave(
      connection: java.sql.Connection,
      tableId: TableId,
      request: InitializeMahjongTableStateRequest
  ): MahjongTableView =
    val ruleset = request.ruleset.getOrElse(MahjongRuleset())
    val seed = request.seed.getOrElse(s"mahjongcore:${tableId.value}")
    val scheduledTable = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, tableId)
    val state = scheduledTable match
      case Some(table) => MahjongGameStateTransitionFunctions.startTable(tableId, ruleset, seed, table.seats)
      case None => MahjongGameStateTransitionFunctions.startTable(tableId, ruleset, seed)
    MahjongTableStateTable.save(connection, state)
    MahjongGameStateTransitionFunctions.toView(state, viewerPlayerId = None, includeLegalActions = true)
