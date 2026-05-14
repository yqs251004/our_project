package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.api.requests.{ProcessAdvancedStatsTasksRequest, RecomputeAdvancedStatsRequest}
import riichinexus.microservices.opsanalytics.objects.AdvancedStatsTaskQuery
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables

object AdvancedStatsApi:

  def listTasks(
      tables: OpsAnalyticsTables,
      query: AdvancedStatsTaskQuery
  ): Vector[AdvancedStatsRecomputeTask] =
    tables.listAdvancedStatsTasks()
      .filter(task => query.status.forall(_ == task.status))

  def taskQueueSummary(
      service: AdvancedStatsPipelineService,
      asOf: Instant
  ): AdvancedStatsTaskQueueSummary =
    service.taskQueueSummary(asOf)

  def recompute(
      service: AdvancedStatsPipelineService,
      request: RecomputeAdvancedStatsRequest,
      requestedAt: Instant
  ): Vector[AdvancedStatsRecomputeTask] =
    (request.ownerType, request.ownerId) match
      case (Some("player"), Some(ownerId)) =>
        Vector(
          service.enqueueOwnerRecompute(
            owner = DashboardOwner.Player(PlayerId(ownerId)),
            reason = request.reason.getOrElse("manual-targeted-recompute"),
            requestedAt = requestedAt
          )
        )
      case (Some("club"), Some(ownerId)) =>
        Vector(
          service.enqueueOwnerRecompute(
            owner = DashboardOwner.Club(ClubId(ownerId)),
            reason = request.reason.getOrElse("manual-targeted-recompute"),
            requestedAt = requestedAt
          )
        )
      case (Some(other), Some(_)) =>
        throw IllegalArgumentException(s"Unsupported advanced stats ownerType: $other")
      case _ =>
        request.parsedMode match
          case AdvancedStatsBackfillMode.Full =>
            service.enqueueFullRecompute(
              requestedAt = requestedAt,
              reason = request.reason.getOrElse("manual-full-recompute")
            )
          case mode =>
            service.enqueueBackfill(
              mode = mode,
              requestedAt = requestedAt,
              reason = request.reason.getOrElse(s"manual-${mode.toString.toLowerCase}-backfill"),
              limit = request.limit
            )

  def processPending(
      service: AdvancedStatsPipelineService,
      request: ProcessAdvancedStatsTasksRequest,
      processedAt: Instant
  ): Vector[AdvancedStatsRecomputeTask] =
    service.processPending(limit = request.limit, processedAt = processedAt)
