package riichinexus.microservices.tournament.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.tournament.TournamentTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class TournamentListAPIMessage(
    status: Option[String] = None,
    adminId: Option[String] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[TournamentSummaryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentSummaryView]] =
    for
      query <- IO(resolveQuery)
      tournaments <- IO(listTournaments(context, query))
    yield pagedResponse(tournaments, query)

  private def resolveQuery: ResolvedTournamentListQuery =
    ResolvedTournamentListQuery(
      query = TournamentListQuery(
        status = status.filter(_.nonEmpty).map(TournamentStatus.valueOf),
        adminId = adminId.filter(_.nonEmpty).map(PlayerId(_)),
        organizer = organizer.filter(_.nonEmpty)
      ),
      limit = limit.getOrElse(20),
      offset = offset.getOrElse(0),
      appliedFilters = filters(
        status.filter(_.nonEmpty).map("status" -> _),
        adminId.filter(_.nonEmpty).map("adminId" -> _),
        organizer.filter(_.nonEmpty).map("organizer" -> _)
      )
    )

  private def listTournaments(
      context: ApiPlanContext,
      resolved: ResolvedTournamentListQuery
  ): Vector[TournamentSummaryView] =
    TournamentTable
      .findFiltered(
        context.connection,
        status = resolved.query.status,
        adminId = resolved.query.adminId,
        organizer = resolved.query.organizer
      )
      .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))
      .map(TournamentSummaryView.fromDomain)

  private def pagedResponse(
      items: Vector[TournamentSummaryView],
      query: ResolvedTournamentListQuery
  ): PagedResponse[TournamentSummaryView] =
    require(query.limit > 0, "Input field limit must be positive")
    require(query.offset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(query.limit, 100)
    val pageItems = items.slice(query.offset, query.offset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, query.offset, query.offset + pageItems.size < items.size, query.appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  private final case class ResolvedTournamentListQuery(
      query: TournamentListQuery,
      limit: Int,
      offset: Int,
      appliedFilters: Map[String, String]
  )
