package riichinexus.microservices.tournament.appeal.api

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class AppealListAPIMessage(
    query: AppealListQuery = AppealListQuery()
) extends APIMessage[PagedResponse[AppealTicketView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AppealTicketView]] =
    for
      resolved <- IO.blocking(resolveQuery)
      appeals <- IO.blocking(listAppeals(context, resolved))
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
    val asOf = resolved.query.asOf.getOrElse(java.time.Instant.now())
    AppealTicketTable.findAll(context.connection)
      .filter(ticket => resolved.query.status.forall(_.toDomain == ticket.status))
      .filter(ticket => resolved.query.priority.forall(_.toDomain == ticket.priority))
      .filter(ticket => resolved.query.tournamentId.forall(_ == ticket.tournamentId))
      .filter(ticket => resolved.query.stageId.forall(_ == ticket.stageId))
      .filter(ticket => resolved.query.tableId.forall(_ == ticket.tableId))
      .filter(ticket => resolved.query.openedBy.forall(_ == ticket.openedBy))
      .filter(ticket => resolved.query.assigneeId.forall(ticket.assigneeId.contains))
      .filter(ticket => !resolved.query.overdueOnly || ticket.dueAt.exists(_.isBefore(asOf)))
      .filter(ticket => resolved.query.dueBefore.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isAfter(limit))))
      .filter(ticket => resolved.query.dueAfter.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isBefore(limit))))
      .sortBy(ticket => (ticket.updatedAt, ticket.id.value))

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
