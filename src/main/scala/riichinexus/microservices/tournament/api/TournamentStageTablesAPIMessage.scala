package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.model.Table

import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.{StageTableQuery, TournamentTableView}

import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.objects.PagedResponse
import upickle.default.ReadWriter

/** 列出指定赛事阶段的牌桌。 */
final case class TournamentStageTablesAPIMessage(
    tournamentId: String,
    stageId: String,
    query: StageTableQuery = StageTableQuery()
) extends APIMessage[PagedResponse[TournamentTableView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentTableView]] =
    for
      resolved <- IO.blocking(resolveQuery)
      tables <- IO.blocking(listStageTables(context, resolved))
    yield PagedResponse.fromItems(tables, resolved.query.limit, resolved.query.offset, resolved.appliedFilters)(TournamentTableView.fromDomain)

  private def resolveQuery: ResolvedStageTablesQuery =
    ResolvedStageTablesQuery(
      tournamentId = TournamentId(tournamentId),
      stageId = TournamentStageId(stageId),
      query = query,
      appliedFilters = Vector(
        query.status.map(value => "status" -> value.toString),
        query.roundNumber.map(value => "roundNumber" -> value.toString),
        query.playerId.map(value => "playerId" -> value.value)
      ).flatten.toMap
    )

  private def listStageTables(
      context: ApiPlanContext,
      resolved: ResolvedStageTablesQuery
  ): Vector[Table] =
    TournamentGameTable
      .findByTournamentAndStage(context.connection, resolved.tournamentId, resolved.stageId)
      .filter(table => resolved.query.status.forall(_ == table.status))
      .filter(table => resolved.query.roundNumber.forall(_ == table.stageRoundNumber))
      .filter(table => resolved.query.playerId.forall(playerId => table.seats.exists(_.playerId == playerId)))
      .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))

  private final case class ResolvedStageTablesQuery(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      query: StageTableQuery,
      appliedFilters: Map[String, String]
  )
