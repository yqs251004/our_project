package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.domain.matchrecord.functions.MatchRecordFunctions

import riichinexus.microservices.tournament.objects.matchrecord.apiTypes.{MatchRecordListQuery, TournamentMatchRecordView}

import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.system.objects.PagedResponse
/** 列出赛事比赛记录。 */
final case class TournamentRecordListAPIMessage(
    query: MatchRecordListQuery = MatchRecordListQuery()
) extends APIMessage[PagedResponse[TournamentMatchRecordView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentMatchRecordView]] =
    for
      resolved <- IO.blocking(resolveQuery)
      records <- IO.blocking(listRecords(context, resolved))
    yield pagedResponse(records, resolved)

  private def resolveQuery: ResolvedMatchRecordListQuery =
    ResolvedMatchRecordListQuery(
      query = query,
      limit = query.limit.getOrElse(20),
      offset = query.offset.getOrElse(0),
      appliedFilters = filters(
        query.playerId.map(value => "playerId" -> value.value),
        query.tournamentId.map(value => "tournamentId" -> value.value),
        query.stageId.map(value => "stageId" -> value.value),
        query.tableId.map(value => "tableId" -> value.value)
      )
    )

  private def listRecords(
      context: ApiPlanContext,
      resolved: ResolvedMatchRecordListQuery
  ): Vector[TournamentMatchRecordView] =
    MatchRecordTable
      .findAll(context.connection)
      .filter(record => resolved.query.playerId.forall(MatchRecordFunctions.playerIds(record).contains))
      .filter(record => resolved.query.tournamentId.forall(_ == record.tournamentId))
      .filter(record => resolved.query.stageId.forall(_ == record.stageId))
      .filter(record => resolved.query.tableId.forall(_ == record.tableId))
      .sortBy(record => (record.generatedAt, record.id.value))
      .map(TournamentViewFunctions.matchRecordView)

  private def pagedResponse(
      items: Vector[TournamentMatchRecordView],
      query: ResolvedMatchRecordListQuery
  ): PagedResponse[TournamentMatchRecordView] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val pageItems = items.slice(query.offset, query.offset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, query.offset, query.offset + pageItems.size < items.size, query.appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  /** 对局记录列表接口解析后的分页查询条件。 */
  private final case class ResolvedMatchRecordListQuery(
      query: MatchRecordListQuery,
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
