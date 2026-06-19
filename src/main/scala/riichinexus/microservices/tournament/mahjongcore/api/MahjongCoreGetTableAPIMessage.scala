package riichinexus.microservices.tournament.mahjongcore.api

import cats.effect.IO
import riichinexus.microservices.auth.domain.authorization.AccessPrincipalFunctions
import riichinexus.microservices.auth.utils.ResolveAccessPrincipal
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions.MahjongGameStateTransitionFunctions
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.{MahjongRuleset, MahjongTableStatus, MahjongTableView}
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
    for
      revealAllHands <- canRevealAllHands(context, query)
      view <- IO.blocking {
        val id = TableId(tableId)
        val state = MahjongTableStateTable.findById(context.connection, id) match
          case Some(current) =>
            val normalized = MahjongGameStateTransitionFunctions.normalizeCurrentRoundState(current)
            if current.status == MahjongTableStatus.Archived then normalized
            else MahjongTableStateTable.save(context.connection, normalized)
          case None => MahjongGameStateTransitionFunctions.notStartedTable(id, MahjongRuleset())
        MahjongGameStateTransitionFunctions.toView(
          state,
          viewerPlayerId = query.viewerPlayerId.map(PlayerId(_)),
          includeLegalActions = query.includeLegalActions,
          revealAllHands = revealAllHands
        )
      }
    yield view

  private def canRevealAllHands(context: ApiPlanContext, query: MahjongTableQuery): IO[Boolean] =
    query.operatorId
      .map(PlayerId(_))
      .map(operatorId => ResolveAccessPrincipal(operatorId).plan(context).attempt.map(_.toOption.exists(AccessPrincipalFunctions.isSuperAdmin)))
      .getOrElse(IO.pure(false))
