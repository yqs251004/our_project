package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.TournamentTableView

import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import upickle.default.ReadWriter

/** 获取赛事牌桌详情。 */
final case class TournamentTableGetAPIMessage(tableId: String) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      id <- IO.blocking(TableId(tableId))
      table <- IO.blocking(resolveTable(context, id))
    yield TournamentTableView.fromDomain(table)

  private def resolveTable(context: ApiPlanContext, tableId: TableId): Table =
    TournamentGameTable.findById(context.connection, tableId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
