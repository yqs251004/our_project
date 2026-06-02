package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRuleset, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.MahjongTableQuery
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 查询 tableId 对应比赛桌的实时麻将桌面视图。 */
final case class MahjongCoreGetTableAPIMessage(
    tableId: String,
    query: MahjongTableQuery = MahjongTableQuery()
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.blocking {
      val id = TableId(tableId)
      val state = MahjongTableStateTable.findById(context.connection, id) match
        case Some(current) =>
          MahjongTableStateTable.save(context.connection, MahjongGameStateTransitionFunctions.normalizeCurrentRoundState(current))
        case None => MahjongGameStateTransitionFunctions.notStartedTable(id, MahjongRuleset())
      MahjongGameStateTransitionFunctions.toView(
        state,
        viewerPlayerId = query.viewerPlayerId.map(PlayerId(_)),
        includeLegalActions = query.includeLegalActions
      )
    }
