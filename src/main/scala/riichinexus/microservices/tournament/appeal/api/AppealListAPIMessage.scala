package riichinexus.microservices.tournament.appeal.api

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealViewFunctions
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket

import riichinexus.microservices.tournament.appeal.objects.apiTypes.{AppealListQuery}
import riichinexus.microservices.tournament.appeal.objects.{AppealTicketView}
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 按筛选条件分页列出申诉工单。 */
final case class AppealListAPIMessage(
    query: AppealListQuery = AppealListQuery()
) extends APIMessage[PagedResponse[AppealTicketView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AppealTicketView]] =
    for
      now <- IO.realTimeInstant
      appliedFilters = filters(
        query.status.map(value => QueryFilterField.toString(QueryFilterField.Status) -> value.toString),
        query.priority.map(value => QueryFilterField.toString(QueryFilterField.Priority) -> value.toString),
        query.tournamentId.map(value => QueryFilterField.toString(QueryFilterField.TournamentId) -> value.value),
        query.stageId.map(value => QueryFilterField.toString(QueryFilterField.StageId) -> value.value),
        query.tableId.map(value => QueryFilterField.toString(QueryFilterField.TableId) -> value.value),
        query.openedBy.map(value => QueryFilterField.toString(QueryFilterField.OpenedBy) -> value.value),
        query.assigneeId.map(value => QueryFilterField.toString(QueryFilterField.AssigneeId) -> value.value),
        Option.when(query.overdueOnly)(QueryFilterField.toString(QueryFilterField.OverdueOnly) -> query.overdueOnly.toString),
        query.dueBefore.map(value => QueryFilterField.toString(QueryFilterField.DueBefore) -> value.toString),
        query.dueAfter.map(value => QueryFilterField.toString(QueryFilterField.DueAfter) -> value.toString),
        query.asOf.map(value => QueryFilterField.toString(QueryFilterField.AsOf) -> value.toString)
      )
      asOf = query.asOf.getOrElse(now)
      appeals <- IO.blocking(listAppeals(context, asOf))
      appealViews = appeals.map(AppealViewFunctions.ticketView)
    yield PagedResponse.fromItems(appealViews, query.limit, query.offset, appliedFilters)(identity)

  private def listAppeals(context: ApiPlanContext, asOf: java.time.Instant): Vector[AppealTicket] =
    AppealTicketTable.findAll(context.connection)
      .filter(ticket => query.status.forall(_ == ticket.status))
      .filter(ticket => query.priority.forall(_ == ticket.priority))
      .filter(ticket => query.tournamentId.forall(_ == ticket.tournamentId))
      .filter(ticket => query.stageId.forall(_ == ticket.stageId))
      .filter(ticket => query.tableId.forall(_ == ticket.tableId))
      .filter(ticket => query.openedBy.forall(_ == ticket.openedBy))
      .filter(ticket => query.assigneeId.forall(ticket.assigneeId.contains))
      .filter(ticket => !query.overdueOnly || ticket.dueAt.exists(_.isBefore(asOf)))
      .filter(ticket => query.dueBefore.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isAfter(limit))))
      .filter(ticket => query.dueAfter.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isBefore(limit))))
      .sortBy(ticket => (ticket.updatedAt, ticket.id.value))

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
