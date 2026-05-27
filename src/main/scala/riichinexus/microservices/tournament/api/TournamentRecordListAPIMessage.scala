package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class TournamentRecordListAPIMessage(
    query: MatchRecordListQuery = MatchRecordListQuery()
) extends APIMessage[PagedResponse[TournamentMatchRecordView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentMatchRecordView]] =
    for
      resolved <- IO(resolveQuery)
      records <- IO(listRecords(context, resolved))
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
      .filter(record => resolved.query.playerId.forall(record.playerIds.contains))
      .filter(record => resolved.query.tournamentId.forall(_ == record.tournamentId))
      .filter(record => resolved.query.stageId.forall(_ == record.stageId))
      .filter(record => resolved.query.tableId.forall(_ == record.tableId))
      .sortBy(record => (record.generatedAt, record.id.value))
      .map(TournamentMatchRecordView.fromDomain)

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

  private final case class ResolvedMatchRecordListQuery(
      query: MatchRecordListQuery,
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
