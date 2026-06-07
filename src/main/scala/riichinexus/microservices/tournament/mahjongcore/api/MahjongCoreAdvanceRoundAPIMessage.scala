package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.MahjongCoreShowcaseMode
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongTableStatus, MahjongTableView}
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes.AdvanceMahjongRoundRequest
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTable
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableId}
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class MahjongCoreAdvanceRoundAPIMessage(
    tableId: String,
    request: Option[AdvanceMahjongRoundRequest] = None
) extends APIMessage[MahjongTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MahjongTableView] =
    IO.blocking {
      val id = TableId(tableId)
      val current = MahjongTableStateTable.findById(context.connection, id)
        .getOrElse(throw IllegalArgumentException(s"Mahjong table ${tableId} is not started"))
      val normalizedCurrent = MahjongGameStateTransitionFunctions.normalizeCurrentRoundState(current)
      val isRoundAdvance =
        normalizedCurrent.status == MahjongTableStatus.RoundEnded &&
          normalizedCurrent.currentRound.exists(_.result.nonEmpty)
      val actor = request.flatMap(_.playerId).filter(_.nonEmpty).map(PlayerId(_))
      if isRoundAdvance then
        val eastPlayer = normalizedCurrent.seats.find(_.seat == SeatWind.East).map(_.playerId)
        if actor.isEmpty || !eastPlayer.contains(actor.get) then
          throw IllegalArgumentException("Only the current east player can advance the mahjong round")
      val nextState = MahjongGameStateTransitionFunctions.advanceRound(
        normalizedCurrent,
        showcaseMode = MahjongCoreShowcaseMode.enabled
      )
      val stored = MahjongTableStateTable.save(context.connection, nextState)
      MahjongGameStateTransitionFunctions.toView(stored, viewerPlayerId = None, includeLegalActions = true)
    }
