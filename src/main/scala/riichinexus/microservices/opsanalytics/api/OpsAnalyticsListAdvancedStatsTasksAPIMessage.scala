package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

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
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.microservices.auth.domain.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsRecomputeTask, AdvancedStatsRecomputeTaskStatus}
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListAdvancedStatsTasksAPIMessage(
    operatorId: PlayerId,
    status: Option[AdvancedStatsRecomputeTaskStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[AdvancedStatsRecomputeTask]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[AdvancedStatsRecomputeTask]] =
    for
      operator <- ResolveAccessPrincipal(operatorId).plan(context)
      _ <- requireOpsAdmin(context, operator)
      query = resolveQuery
      tasks <- IO.blocking(listTasks(context, query))
    yield paged(tasks, query)

  private def resolveQuery: AdvancedStatsTasksQuery =
    AdvancedStatsTasksQuery(
      status = status,
      appliedFilters = Map.from(status.map(value => "status" -> AdvancedStatsRecomputeTaskStatus.toString(value)))
    )

  private def listTasks(
      context: ApiPlanContext,
      query: AdvancedStatsTasksQuery
  ): Vector[AdvancedStatsRecomputeTask] =
    AdvancedStatsRecomputeTaskTable
      .findAll(context.connection)
      .filter(task => query.status.forall(_ == task.status))

  private def requireOpsAdmin(context: ApiPlanContext, operator: AccessPrincipal): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      principal = Some(operator),
      permission = Permission.ManagePlatformOperations
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to manage platform operations"))
    }

  private def paged(
      items: Vector[AdvancedStatsRecomputeTask],
      query: AdvancedStatsTasksQuery
  ): PagedResponse[AdvancedStatsRecomputeTask] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    if resolvedLimit <= 0 then throw IllegalArgumentException("Input field limit must be positive")
    if resolvedOffset < 0 then throw IllegalArgumentException("Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, query.appliedFilters)

  private final case class AdvancedStatsTasksQuery(
      status: Option[AdvancedStatsRecomputeTaskStatus],
      appliedFilters: Map[String, String]
  )
