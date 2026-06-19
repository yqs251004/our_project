package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.microservices.tournament.objects.stage.table.apiTypes.{TableListQuery, TournamentTableView}

import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.objects.PagedResponse
import upickle.default.ReadWriter

/** 列出赛事牌桌。 */
final case class TournamentTableListAPIMessage(
    query: TableListQuery = TableListQuery()
) extends APIMessage[PagedResponse[TournamentTableView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentTableView]] =
    for
      resolved <- IO.blocking(resolveQuery)
      tables <- IO.blocking(listTables(context, resolved))
    yield PagedResponse.fromItems(tables, resolved.query.limit, resolved.query.offset, resolved.appliedFilters)(TournamentTableView.fromDomain)

  private def resolveQuery: ResolvedTableListQuery =
    ResolvedTableListQuery(
      query = query,
      appliedFilters = Vector(
        query.status.map(value => "status" -> value.toString),
        query.tournamentId.map(value => "tournamentId" -> value.value),
        query.stageId.map(value => "stageId" -> value.value),
        query.roundNumber.map(value => "roundNumber" -> value.toString),
        query.playerId.map(value => "playerId" -> value.value)
      ).flatten.toMap
    )

  private def listTables(
      context: ApiPlanContext,
      resolved: ResolvedTableListQuery
  ): Vector[Table] =
    TournamentGameTable
      .findAll(context.connection)
      .filter(table => resolved.query.status.forall(_ == table.status))
      .filter(table => resolved.query.tournamentId.forall(_ == table.tournamentId))
      .filter(table => resolved.query.stageId.forall(_ == table.stageId))
      .filter(table => resolved.query.roundNumber.forall(_ == table.stageRoundNumber))
      .filter(table => resolved.query.playerId.forall(playerId => table.seats.exists(_.playerId == playerId)))
      .sortBy(table =>
        (table.tournamentId.value, table.stageId.value, table.stageRoundNumber, table.tableNo, table.id.value)
      )

  private final case class ResolvedTableListQuery(
      query: TableListQuery,
      appliedFilters: Map[String, String]
  )
