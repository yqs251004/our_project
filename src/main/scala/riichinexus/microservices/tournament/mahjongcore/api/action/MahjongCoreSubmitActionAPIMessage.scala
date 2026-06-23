package riichinexus.microservices.tournament.mahjongcore.api.action

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.MahjongSubmittedAction
import riichinexus.microservices.tournament.mahjongcore.domain.realtime.functions.MahjongRealtimeEventFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongPublicEventView
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.{MahjongActionResponse, SubmitMahjongActionRequest}
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
/** 提交玩家实时麻将行动并返回最新桌面。 */
final case class MahjongCoreSubmitActionAPIMessage(
    tableId: String,
    request: SubmitMahjongActionRequest
) extends APIMessage[MahjongActionResponse]:

  override def plan(context: ApiPlanContext): IO[MahjongActionResponse] =
    for
      requestedTableId <- IO.delay(TableId(tableId))
      submittedAction <- IO.delay(buildSubmittedAction)
      outcome <- IO.blocking(submitAndSave(context, requestedTableId, submittedAction))
      occurredAt <- IO.realTimeInstant
      response = outcome._1
      acceptedEvent = outcome._2
      _ <- acceptedEvent.fold(IO.unit)(event => publishAcceptedAction(context, event, occurredAt))
    yield response

  private def buildSubmittedAction: MahjongSubmittedAction =
    MahjongSubmittedAction(
      playerId = PlayerId(request.playerId),
      commandType = request.commandType,
      tile = request.tile,
      tiles = request.tiles,
      targetSequenceNo = request.targetSequenceNo
    )

  private def submitAndSave(
      context: ApiPlanContext,
      tableId: TableId,
      submitted: MahjongSubmittedAction
  ): (MahjongActionResponse, Option[MahjongPublicEventView]) =
    val current = MahjongTableStateTable
      .findById(context.connection, tableId)
      .getOrElse(throw IllegalArgumentException(s"Mahjong table ${tableId.value} is not started"))
    val normalizedCurrent = MahjongTableStateTable.save(
      context.connection,
      MahjongGameStateTransitionFunctions.normalizeCurrentRoundState(current)
    )
    val (state, acceptedEvent) = MahjongGameStateTransitionFunctions.submitAction(normalizedCurrent, submitted)
    val stored = MahjongTableStateTable.save(context.connection, state)
    MahjongActionResponse(
      table = MahjongGameStateTransitionFunctions.toView(stored, Some(submitted.playerId), includeLegalActions = true),
      acceptedEvent = acceptedEvent,
      archivedPaifuId = None
    )
      -> acceptedEvent

  private def publishAcceptedAction(
      context: ApiPlanContext,
      event: MahjongPublicEventView,
      occurredAt: Instant
  ): IO[Unit] =
    context.afterCommit(
      context.realtimeEventBus.publish(
        MahjongRealtimeEventFunctions.actionAccepted(TableId(tableId), event, occurredAt)
      )
    )
