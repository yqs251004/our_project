package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.paifu.PaifuTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class TournamentPaifuListAPIMessage(
    query: PaifuListQuery = PaifuListQuery()
) extends APIMessage[PagedResponse[TournamentPaifuSummaryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentPaifuSummaryView]] =
    for
      resolved <- IO.blocking(resolveQuery)
      paifus <- IO.blocking(listPaifus(context, resolved))
    yield pagedResponse(paifus, resolved)

  private def resolveQuery: ResolvedPaifuListQuery =
    ResolvedPaifuListQuery(
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

  private def listPaifus(
      context: ApiPlanContext,
      resolved: ResolvedPaifuListQuery
  ): Vector[TournamentPaifuSummaryView] =
    PaifuTable
      .findAll(context.connection)
      .filter(paifu => resolved.query.playerId.forall(paifu.playerIds.contains))
      .filter(paifu => resolved.query.tournamentId.forall(_ == paifu.metadata.tournamentId))
      .filter(paifu => resolved.query.stageId.forall(_ == paifu.metadata.stageId))
      .filter(paifu => resolved.query.tableId.forall(_ == paifu.metadata.tableId))
      .sortBy(paifu => (paifu.metadata.recordedAt, paifu.id.value))
      .map(TournamentPaifuSummaryView.fromDomain)

  private def pagedResponse(
      items: Vector[TournamentPaifuSummaryView],
      query: ResolvedPaifuListQuery
  ): PagedResponse[TournamentPaifuSummaryView] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val pageItems = items.slice(query.offset, query.offset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, query.offset, query.offset + pageItems.size < items.size, query.appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  private final case class ResolvedPaifuListQuery(
      query: PaifuListQuery,
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
