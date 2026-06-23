package riichinexus.microservices.tournament.api.matchrecord
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.domain.matchrecord.functions.MatchRecordFunctions

import riichinexus.microservices.tournament.objects.matchrecord.apiTypes.{MatchRecordListQuery}
import riichinexus.microservices.tournament.objects.matchrecord.{TournamentMatchRecordView}

import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出赛事比赛记录。 */
final case class TournamentRecordListAPIMessage(
  query: MatchRecordListQuery = MatchRecordListQuery()
) extends APIMessage[PagedResponse[TournamentMatchRecordView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentMatchRecordView]] =
    val appliedFilters = filters(
      query.playerId.map(value => QueryFilterField.toString(QueryFilterField.PlayerId) -> value.value),
      query.tournamentId.map(value => QueryFilterField.toString(QueryFilterField.TournamentId) -> value.value),
      query.stageId.map(value => QueryFilterField.toString(QueryFilterField.StageId) -> value.value),
      query.tableId.map(value => QueryFilterField.toString(QueryFilterField.TableId) -> value.value)
    )
    for
      records <- IO.blocking(listRecords(context))
    yield PagedResponse.fromItems(records, query.limit, query.offset, appliedFilters)(identity)

  private def listRecords(
      context: ApiPlanContext
  ): Vector[TournamentMatchRecordView] =
    MatchRecordTable
      .findAll(context.connection)
      .filter(record => query.playerId.forall(MatchRecordFunctions.playerIds(record).contains))
      .filter(record => query.tournamentId.forall(_ == record.tournamentId))
      .filter(record => query.stageId.forall(_ == record.stageId))
      .filter(record => query.tableId.forall(_ == record.tableId))
      .sortBy(record => (record.generatedAt, record.id.value))
      .map(TournamentViewFunctions.matchRecordView)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
