package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.MahjongSubmittedAction
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.{MahjongActionResponse, SubmitMahjongActionRequest}
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

/** 提交玩家实时麻将行动并返回最新桌面。 */
final case class MahjongCoreSubmitActionAPIMessage(
    tableId: String,
    request: SubmitMahjongActionRequest
) extends APIMessage[MahjongActionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongActionResponse] =
    IO.blocking {
      val id = TableId(tableId)
      val submitted = MahjongSubmittedAction(
        playerId = PlayerId(request.playerId),
        commandType = request.commandType,
        tile = request.tile,
        tiles = request.tiles,
        targetSequenceNo = request.targetSequenceNo
      )
      val current = MahjongTableStateTable.findById(context.connection, id)
        .getOrElse(throw IllegalArgumentException(s"Mahjong table ${tableId} is not started"))
      val (state, acceptedEvent) = MahjongGameStateTransitionFunctions.submitAction(current, submitted)
      MahjongTableStateTable.save(context.connection, state)
      MahjongActionResponse(
        table = MahjongGameStateTransitionFunctions.toView(state, Some(submitted.playerId), includeLegalActions = true),
        acceptedEvent = acceptedEvent,
        archivedPaifuId = None
      )
    }
