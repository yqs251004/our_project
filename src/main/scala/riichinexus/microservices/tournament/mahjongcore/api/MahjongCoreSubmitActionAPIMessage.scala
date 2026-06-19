package riichinexus.microservices.tournament.mahjongcore.api

import java.time.Instant

import cats.effect.IO
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.MahjongSubmittedAction
import riichinexus.microservices.tournament.mahjongcore.objects.action.MahjongPublicEventView
import riichinexus.microservices.tournament.mahjongcore.objects.action.apiTypes.{MahjongActionResponse, SubmitMahjongActionRequest}
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.system.api.{APIMessage, ApiPlanContext}

import riichinexus.system.realtime.objects.RealtimeEvent
import upickle.default.{ReadWriter, writeJs}

/** 提交玩家实时麻将行动并返回最新桌面。 */
final case class MahjongCoreSubmitActionAPIMessage(
    tableId: String,
    request: SubmitMahjongActionRequest
) extends APIMessage[MahjongActionResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongActionResponse] =
    for
      command <- IO.delay(resolveCommand)
      outcome <- IO.blocking(submitAndSave(context, command))
      occurredAt <- IO.realTimeInstant
      _ <- outcome.acceptedEvent.fold(IO.unit)(event => publishAcceptedAction(context, event, occurredAt))
    yield outcome.response

  private def resolveCommand: SubmitMahjongActionCommand =
    SubmitMahjongActionCommand(
      tableId = TableId(tableId),
      submitted = MahjongSubmittedAction(
        playerId = PlayerId(request.playerId),
        commandType = request.commandType,
        tile = request.tile,
        tiles = request.tiles,
        targetSequenceNo = request.targetSequenceNo
      )
    )

  private def submitAndSave(
      context: ApiPlanContext,
      command: SubmitMahjongActionCommand
  ): SubmitMahjongActionOutcome =
    val current = MahjongTableStateTable
      .findById(context.connection, command.tableId)
      .getOrElse(throw IllegalArgumentException(s"Mahjong table ${command.tableId.value} is not started"))
    val normalizedCurrent = MahjongTableStateTable.save(
      context.connection,
      MahjongGameStateTransitionFunctions.normalizeCurrentRoundState(current)
    )
    val (state, acceptedEvent) = MahjongGameStateTransitionFunctions.submitAction(normalizedCurrent, command.submitted)
    val stored = MahjongTableStateTable.save(context.connection, state)
    SubmitMahjongActionOutcome(
      response = MahjongActionResponse(
        table = MahjongGameStateTransitionFunctions.toView(stored, Some(command.submitted.playerId), includeLegalActions = true),
        acceptedEvent = acceptedEvent,
        archivedPaifuId = None
      ),
      acceptedEvent = acceptedEvent
    )

  private def publishAcceptedAction(
      context: ApiPlanContext,
      event: MahjongPublicEventView,
      occurredAt: Instant
  ): IO[Unit] =
    context.afterCommit(
      context.realtimeEventBus.publish(
        RealtimeEvent(
          id = s"mahjong-action:${tableId}:${event.sequenceNo}",
          eventType = "MahjongActionAccepted",
          aggregateType = "mahjongTable",
          aggregateId = tableId,
          occurredAt = occurredAt,
          sourceEventType = event.actionType.toString,
          actorId = event.actor.map(_.value),
          actionUrl = Some(s"/tables/${tableId}"),
          data = Some(writeJs(event))
        )
      )
    )

  private final case class SubmitMahjongActionCommand(
      tableId: TableId,
      submitted: MahjongSubmittedAction
  )

  private final case class SubmitMahjongActionOutcome(
      response: MahjongActionResponse,
      acceptedEvent: Option[MahjongPublicEventView]
  )
