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
      val id = TableId(tableId)
      val ruleset = request.ruleset.getOrElse(MahjongRuleset())
      val seed = request.seed.getOrElse(s"mahjongcore:${tableId}")
      val scheduledTable = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findById(context.connection, id)
      val state = scheduledTable match
        case Some(table) => MahjongGameStateTransitionFunctions.startTable(id, ruleset, seed, table.seats)
        case None => MahjongGameStateTransitionFunctions.startTable(id, ruleset, seed)
      MahjongTableStateTable.save(context.connection, state)
      MahjongGameStateTransitionFunctions.toView(state, viewerPlayerId = None, includeLegalActions = true)
    }
