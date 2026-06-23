package riichinexus.microservices.tournament.api.stage.table
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.microservices.tournament.objects.stage.table.apiTypes.{TableListQuery}
import riichinexus.microservices.tournament.objects.stage.table.{TournamentTableView}

import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出赛事牌桌。 */
final case class TournamentTableListAPIMessage(
    query: TableListQuery = TableListQuery()
) extends APIMessage[PagedResponse[TournamentTableView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentTableView]] =
    val appliedFilters = Vector(
      query.status.map(value => QueryFilterField.toString(QueryFilterField.Status) -> value.toString),
      query.tournamentId.map(value => QueryFilterField.toString(QueryFilterField.TournamentId) -> value.value),
      query.stageId.map(value => QueryFilterField.toString(QueryFilterField.StageId) -> value.value),
      query.roundNumber.map(value => QueryFilterField.toString(QueryFilterField.RoundNumber) -> value.toString),
      query.playerId.map(value => QueryFilterField.toString(QueryFilterField.PlayerId) -> value.value)
    ).flatten.toMap
    for
      tables <- IO.blocking(listTables(context))
    yield PagedResponse.fromItems(tables, query.limit, query.offset, appliedFilters)(TournamentViewFunctions.tableView)

  private def listTables(
      context: ApiPlanContext
  ): Vector[Table] =
    TournamentGameTable
      .findAll(context.connection)
      .filter(table => query.status.forall(_ == table.status))
      .filter(table => query.tournamentId.forall(_ == table.tournamentId))
      .filter(table => query.stageId.forall(_ == table.stageId))
      .filter(table => query.roundNumber.forall(_ == table.stageRoundNumber))
      .filter(table => query.playerId.forall(playerId => table.seats.exists(_.playerId == playerId)))
      .sortBy(table =>
        (table.tournamentId.value, table.stageId.value, table.stageRoundNumber, table.tableNo, table.id.value)
      )
