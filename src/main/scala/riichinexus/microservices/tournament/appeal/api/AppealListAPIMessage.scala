package riichinexus.microservices.tournament.appeal.api

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.*
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class AppealListAPIMessage(
    query: AppealListQuery = AppealListQuery()
) extends APIMessage[PagedResponse[AppealTicketView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AppealTicketView]] =
    for
      resolved <- IO(resolveQuery)
      appeals <- IO(listAppeals(context, resolved))
    yield page(appeals.map(AppealTicketView.fromDomain), resolved)

  private def resolveQuery: ResolvedAppealListQuery =
    ResolvedAppealListQuery(
      query = query,
      appliedFilters = filters(
        query.status.map(value => "status" -> value.toString),
        query.priority.map(value => "priority" -> value.toString),
        query.tournamentId.map(value => "tournamentId" -> value.value),
        query.stageId.map(value => "stageId" -> value.value),
        query.tableId.map(value => "tableId" -> value.value),
        query.openedBy.map(value => "openedBy" -> value.value),
        query.assigneeId.map(value => "assigneeId" -> value.value),
        Option.when(query.overdueOnly)("overdueOnly" -> query.overdueOnly.toString),
        query.dueBefore.map(value => "dueBefore" -> value.toString),
        query.dueAfter.map(value => "dueAfter" -> value.toString),
        query.asOf.map(value => "asOf" -> value.toString)
      )
    )

  private def listAppeals(context: ApiPlanContext, resolved: ResolvedAppealListQuery): Vector[AppealTicket] =
    context.support.tournamentAppealModule.tables.listAppeals(resolved.query)

  private def page(items: Vector[AppealTicketView], resolved: ResolvedAppealListQuery): PagedResponse[AppealTicketView] =
    val resolvedLimit = resolved.query.limit.getOrElse(20)
    val resolvedOffset = resolved.query.offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, resolved.appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  private final case class ResolvedAppealListQuery(
      query: AppealListQuery,
      appliedFilters: Map[String, String]
  )
