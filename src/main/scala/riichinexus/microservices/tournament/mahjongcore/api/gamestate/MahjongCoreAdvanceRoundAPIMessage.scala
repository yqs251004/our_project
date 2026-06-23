package riichinexus.microservices.tournament.mahjongcore.api.gamestate
import cats.effect.IO
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.domain.realtime.functions.MahjongRealtimeEventFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongTableStatus, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.AdvanceMahjongRoundRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.stage.table.{SeatWind, TableId}
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.app.MahjongCoreShowcaseModeState
import riichinexus.system.json.JsonCodecs.given
import riichinexus.system.realtime.objects.RealtimeSourceEventType
/** 推进已结束的小局，或在初始/等待状态下开下一局。 */
final case class MahjongCoreAdvanceRoundAPIMessage(
    tableId: String,
    request: Option[AdvanceMahjongRoundRequest] = None
) extends APIMessage[MahjongTableView]:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    for
      requestedTableId <- IO.delay(TableId(tableId))
      actor = request.flatMap(_.playerId).filter(_.nonEmpty).map(PlayerId(_))
      showcaseMode <- IO.delay(MahjongCoreShowcaseModeState.enabled)
      stored <- IO.blocking(advanceAndSave(context, requestedTableId, actor, showcaseMode))
      occurredAt <- IO.realTimeInstant
      tableView = MahjongGameStateTransitionFunctions.toView(stored, viewerPlayerId = None, includeLegalActions = true)
      _ <- context.afterCommit(
        context.realtimeEventBus.publish(
          MahjongRealtimeEventFunctions.tableChanged(
            tableId = requestedTableId,
            sourceEventType = RealtimeSourceEventType.MahjongTableRoundAdvanced,
            table = tableView,
            actorId = actor,
            occurredAt = occurredAt
          )
        )
      )
    yield tableView

  private def advanceAndSave(
      context: ApiPlanContext,
      tableId: TableId,
      actor: Option[PlayerId],
      showcaseMode: Boolean
  ) =
    val current = MahjongTableStateTable
      .findById(context.connection, tableId)
      .getOrElse(throw IllegalArgumentException(s"Mahjong table ${tableId.value} is not started"))
    val normalizedCurrent = MahjongGameStateTransitionFunctions.normalizeCurrentRoundState(current)
    requireCurrentEastForRoundAdvance(normalizedCurrent, actor)
    val nextState = MahjongGameStateTransitionFunctions.advanceRound(normalizedCurrent, showcaseMode = showcaseMode)
    MahjongTableStateTable.save(context.connection, nextState)

  private def requireCurrentEastForRoundAdvance(
      state: riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model.MahjongTableState,
      actor: Option[PlayerId]
  ): Unit =
    val isRoundAdvance =
      state.status == MahjongTableStatus.RoundEnded &&
        state.currentRound.exists(_.result.nonEmpty)
    if isRoundAdvance then
      val eastPlayer = state.seats.find(_.seat == SeatWind.East).map(_.playerId)
      if actor.isEmpty || !eastPlayer.contains(actor.get) then
        throw IllegalArgumentException("Only the current east player can advance the mahjong round")

