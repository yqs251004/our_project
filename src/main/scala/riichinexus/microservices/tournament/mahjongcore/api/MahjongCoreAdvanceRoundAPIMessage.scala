package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongTableStatus, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.AdvanceMahjongRoundRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableId}
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.app.MahjongCoreShowcaseModeState
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 推进已结束的小局，或在初始/等待状态下开下一局。 */
final case class MahjongCoreAdvanceRoundAPIMessage(
    tableId: String,
    request: Option[AdvanceMahjongRoundRequest] = None
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    for
      command <- IO.delay(resolveCommand)
      showcaseMode <- IO.delay(MahjongCoreShowcaseModeState.enabled)
      stored <- IO.blocking(advanceAndSave(context, command, showcaseMode))
    yield MahjongGameStateTransitionFunctions.toView(stored, viewerPlayerId = None, includeLegalActions = true)

  private def resolveCommand: AdvanceMahjongRoundCommand =
    AdvanceMahjongRoundCommand(
      tableId = TableId(tableId),
      actor = request.flatMap(_.playerId).filter(_.nonEmpty).map(PlayerId(_))
    )

  private def advanceAndSave(
      context: ApiPlanContext,
      command: AdvanceMahjongRoundCommand,
      showcaseMode: Boolean
  ) =
    val current = MahjongTableStateTable
      .findById(context.connection, command.tableId)
      .getOrElse(throw IllegalArgumentException(s"Mahjong table ${command.tableId.value} is not started"))
    val normalizedCurrent = MahjongGameStateTransitionFunctions.normalizeCurrentRoundState(current)
    requireCurrentEastForRoundAdvance(normalizedCurrent, command.actor)
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

  private final case class AdvanceMahjongRoundCommand(
      tableId: TableId,
      actor: Option[PlayerId]
  )
