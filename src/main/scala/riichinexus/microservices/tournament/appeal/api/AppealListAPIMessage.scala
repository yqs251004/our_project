package riichinexus.microservices.tournament.appeal.api

import java.time.Instant

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.*
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class AppealListAPIMessage(
    status: Option[String] = None,
    priority: Option[String] = None,
    tournamentId: Option[String] = None,
    stageId: Option[String] = None,
    tableId: Option[String] = None,
    openedBy: Option[String] = None,
    assigneeId: Option[String] = None,
    overdueOnly: Option[Boolean] = None,
    dueBefore: Option[String] = None,
    dueAfter: Option[String] = None,
    asOf: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[AppealTicketView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AppealTicketView]] =
    IO {
      val query = AppealListQuery(
        status = status.filter(_.nonEmpty).map(AppealStatus.valueOf),
        priority = priority.filter(_.nonEmpty).map(AppealPriority.valueOf),
        tournamentId = tournamentId.filter(_.nonEmpty).map(TournamentId(_)),
        stageId = stageId.filter(_.nonEmpty).map(TournamentStageId(_)),
        tableId = tableId.filter(_.nonEmpty).map(TableId(_)),
        openedBy = openedBy.filter(_.nonEmpty).map(PlayerId(_)),
        assigneeId = assigneeId.filter(_.nonEmpty).map(PlayerId(_)),
        overdueOnly = overdueOnly.contains(true),
        dueBefore = dueBefore.filter(_.nonEmpty).map(Instant.parse),
        dueAfter = dueAfter.filter(_.nonEmpty).map(Instant.parse),
        asOf = asOf.filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
      )
      val appeals = context.support.tournamentAppealModule.tables.listAppeals(query).map(AppealTicketView.fromDomain)
      page(appeals, filters(
        status.filter(_.nonEmpty).map("status" -> _),
        priority.filter(_.nonEmpty).map("priority" -> _),
        tournamentId.filter(_.nonEmpty).map("tournamentId" -> _),
        stageId.filter(_.nonEmpty).map("stageId" -> _),
        tableId.filter(_.nonEmpty).map("tableId" -> _),
        openedBy.filter(_.nonEmpty).map("openedBy" -> _),
        assigneeId.filter(_.nonEmpty).map("assigneeId" -> _),
        overdueOnly.map(value => "overdueOnly" -> value.toString),
        dueBefore.filter(_.nonEmpty).map("dueBefore" -> _),
        dueAfter.filter(_.nonEmpty).map("dueAfter" -> _),
        asOf.filter(_.nonEmpty).map("asOf" -> _)
      ))
    }

  private def page(items: Vector[AppealTicketView], appliedFilters: Map[String, String]): PagedResponse[AppealTicketView] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
