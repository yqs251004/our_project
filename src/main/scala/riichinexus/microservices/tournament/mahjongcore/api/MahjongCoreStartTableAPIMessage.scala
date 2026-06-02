package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRuleset, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.StartMahjongTableRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 启动 tableId 对应比赛桌的实时麻将对局。 */
final case class MahjongCoreStartTableAPIMessage(
    tableId: String,
    request: StartMahjongTableRequest
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.blocking {
      MahjongCoreStartTableAPIMessage.startAndSave(
        context.connection,
        TableId(tableId),
        request
      )
    }

object MahjongCoreStartTableAPIMessage:

  def startAndSave(
      connection: java.sql.Connection,
      tableId: TableId,
      request: StartMahjongTableRequest
  ): MahjongTableView =
    val ruleset = request.ruleset.getOrElse(MahjongRuleset())
    val seed = request.seed.getOrElse(s"mahjongcore:${tableId.value}")
    val scheduledTable = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(connection, tableId)
    val state = scheduledTable match
      case Some(table) => MahjongGameStateTransitionFunctions.startTable(tableId, ruleset, seed, table.seats)
      case None => MahjongGameStateTransitionFunctions.startTable(tableId, ruleset, seed)
    MahjongTableStateTable.save(connection, state)
    MahjongGameStateTransitionFunctions.toView(state, viewerPlayerId = None, includeLegalActions = true)
