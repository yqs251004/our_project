package riichinexus.microservices.tournament.api.stage.table
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.microservices.tournament.objects.stage.table.apiTypes.{StageTableQuery}
import riichinexus.microservices.tournament.objects.stage.table.{TournamentTableView}

import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出指定赛事阶段的牌桌。 */
final case class TournamentStageTablesAPIMessage(
    tournamentId: String,
    stageId: String,
    query: StageTableQuery = StageTableQuery()
) extends APIMessage[PagedResponse[TournamentTableView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentTableView]] =
    for
      requestedTournamentId <- IO.blocking(TournamentId(tournamentId))
      requestedStageId <- IO.blocking(TournamentStageId(stageId))
      appliedFilters = Vector(
        query.status.map(value => QueryFilterField.toString(QueryFilterField.Status) -> value.toString),
        query.roundNumber.map(value => QueryFilterField.toString(QueryFilterField.RoundNumber) -> value.toString),
        query.playerId.map(value => QueryFilterField.toString(QueryFilterField.PlayerId) -> value.value)
      ).flatten.toMap
      tables <- IO.blocking(listStageTables(context, requestedTournamentId, requestedStageId))
    yield PagedResponse.fromItems(tables, query.limit, query.offset, appliedFilters)(TournamentViewFunctions.tableView)

  private def listStageTables(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    TournamentGameTable
      .findByTournamentAndStage(context.connection, tournamentId, stageId)
      .filter(table => query.status.forall(_ == table.status))
      .filter(table => query.roundNumber.forall(_ == table.stageRoundNumber))
      .filter(table => query.playerId.forall(playerId => table.seats.exists(_.playerId == playerId)))
      .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
