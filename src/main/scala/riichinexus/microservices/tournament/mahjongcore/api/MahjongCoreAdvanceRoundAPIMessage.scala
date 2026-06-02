package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongTableView
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class MahjongCoreAdvanceRoundAPIMessage(
    tableId: String
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.blocking {
      val id = TableId(tableId)
      val current = MahjongTableStateTable.findById(context.connection, id)
        .getOrElse(throw IllegalArgumentException(s"Mahjong table ${tableId} is not started"))
      val nextState = MahjongGameStateTransitionFunctions.advanceRound(current)
      val stored = MahjongTableStateTable.save(context.connection, nextState)
      MahjongGameStateTransitionFunctions.toView(stored, viewerPlayerId = None, includeLegalActions = true)
    }
