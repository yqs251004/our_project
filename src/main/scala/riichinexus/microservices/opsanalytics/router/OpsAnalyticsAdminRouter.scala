package riichinexus.microservices.opsanalytics.router

import java.time.Instant

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.{
  AdvancedStatsApi,
  AdvancedStatsPipelineService,
  DomainEventQueryService,
  DomainEventOperationsService,
  DomainEventOpsApi,
  PerformanceApi,
  PerformanceDiagnosticsService
}
import riichinexus.microservices.opsanalytics.api.requests.*
import riichinexus.microservices.opsanalytics.api.responses.DomainEventResponses.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsTaskQuery, DomainEventOutboxQuery, EventCascadeRecordQuery}
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables
import riichinexus.api.http.RouteSupport

object OpsAnalyticsAdminRouter:
  private final case class Dependencies(
      tables: OpsAnalyticsTables,
      advancedStatsService: AdvancedStatsPipelineService,
      domainEventQueryService: DomainEventQueryService,
      domainEventService: DomainEventOperationsService,
      performanceDiagnosticsService: PerformanceDiagnosticsService
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.opsAnalyticsModule
    Dependencies(
      tables = module.tables,
      advancedStatsService = module.advancedStatsService,
      domainEventQueryService = module.domainEventQueryService,
      domainEventService = module.domainEventService,
      performanceDiagnosticsService = module.performanceDiagnosticsService
    )

  def routes(support: RouteSupport): HttpRoutes[IO] =
    val deps = dependencies(support)
    HttpRoutes.of[IO] {
    case req @ GET -> Root / "admin" / "advanced-stats" / "tasks" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val query = AdvancedStatsTaskQuery(
          status = support.queryParam(req, "status").map(AdvancedStatsRecomputeTaskStatus.valueOf)
        )
        val tasks = AdvancedStatsApi.listTasks(deps.tables, query)
        support.pagedJsonResponse(req, tasks, support.activeFilters(req, "status"))
      }

    case req @ GET -> Root / "admin" / "advanced-stats" / "summary" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        support.jsonResponse(Status.Ok, AdvancedStatsApi.taskQueueSummary(deps.advancedStatsService, asOf))
      }

    case req @ POST -> Root / "admin" / "advanced-stats" / "recompute" =>
      support.handled {
        support.readJsonBody[RecomputeAdvancedStatsRequest](req).flatMap { request =>
          val operator = support.principal(request.operator)
          support.requirePermission(operator, Permission.ManageGlobalDictionary)
          val tasks = AdvancedStatsApi.recompute(deps.advancedStatsService, request, Instant.now())
          support.jsonResponse(Status.Accepted, tasks)
        }
      }

    case req @ POST -> Root / "admin" / "advanced-stats" / "process" =>
      support.handled {
        support.readJsonBody[ProcessAdvancedStatsTasksRequest](req).flatMap { request =>
          val operator = support.principal(request.operator)
          support.requirePermission(operator, Permission.ManageGlobalDictionary)
          support.jsonResponse(Status.Ok, AdvancedStatsApi.processPending(deps.advancedStatsService, request, Instant.now()))
        }
      }

    case req @ GET -> Root / "admin" / "performance" / "summary" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val limit = support.queryIntParam(req, "limit").getOrElse(15)
        require(limit > 0, "Query parameter limit must be positive")
        support.jsonResponse(Status.Ok, PerformanceApi.snapshot(deps.performanceDiagnosticsService, limit))
      }

    case req @ GET -> Root / "admin" / "domain-events" / "summary" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        support.jsonResponse(Status.Ok, DomainEventOpsApi.summary(deps.domainEventQueryService, asOf))
      }

    case req @ GET -> Root / "admin" / "domain-events" / "outbox" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val query = DomainEventOutboxQuery(
          asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now()),
          status = support.queryParam(req, "status").map(DomainEventOutboxStatus.valueOf),
          eventType = support.queryParam(req, "eventType").filter(_.nonEmpty),
          aggregateType = support.queryParam(req, "aggregateType").filter(_.nonEmpty),
          aggregateId = support.queryParam(req, "aggregateId").filter(_.nonEmpty),
          subscriberId = support.queryParam(req, "subscriberId").filter(_.nonEmpty),
          partitionKey = support.queryParam(req, "partitionKey").filter(_.nonEmpty),
          delivered = support.queryBooleanParam(req, "delivered"),
          blockedOnly = support.queryBooleanParam(req, "blockedOnly").getOrElse(false)
        )
        require(query.subscriberId.nonEmpty || query.delivered.isEmpty, "Query parameter delivered requires subscriberId")
        require(query.subscriberId.nonEmpty || query.partitionKey.isEmpty, "Query parameter partitionKey requires subscriberId")
        require(query.subscriberId.nonEmpty || !query.blockedOnly, "Query parameter blockedOnly requires subscriberId")
        val records = DomainEventOpsApi.outboxRecords(deps.domainEventQueryService, query)
        support.pagedJsonResponse(
          req,
          records,
          support.activeFilters(
            req,
            "asOf",
            "status",
            "eventType",
            "aggregateType",
            "aggregateId",
            "subscriberId",
            "partitionKey",
            "delivered",
            "blockedOnly"
          )
        )
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / "replay" =>
      support.handled {
        support.readJsonBody[BatchReplayDomainEventOutboxRequest](req).flatMap { request =>
          val actor = support.principal(request.operator)
          support.jsonResponse(Status.Ok, DomainEventOpsApi.replayOutboxRecords(deps.domainEventService, actor, request))
        }
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / "ack" =>
      support.handled {
        support.readJsonBody[BatchAcknowledgeDomainEventOutboxRequest](req).flatMap { request =>
          val actor = support.principal(request.operator)
          support.jsonResponse(Status.Ok, DomainEventOpsApi.acknowledgeOutboxRecords(deps.domainEventService, actor, request))
        }
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / "quarantine" =>
      support.handled {
        support.readJsonBody[BatchQuarantineDomainEventOutboxRequest](req).flatMap { request =>
          val actor = support.principal(request.operator)
          support.jsonResponse(Status.Ok, DomainEventOpsApi.quarantineOutboxRecords(deps.domainEventService, actor, request))
        }
      }

    case req @ GET -> Root / "admin" / "domain-events" / "outbox" / recordId / "history" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.jsonResponse(
          Status.Ok,
          DomainEventOpsApi.outboxHistory(deps.domainEventQueryService, DomainEventOutboxRecordId(recordId), operator)
        )
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / recordId / "replay" =>
      support.handled {
        support.readJsonBody[ReplayDomainEventOutboxRequest](req).flatMap { request =>
          val actor = support.principal(request.operator)
          support.jsonResponse(
            Status.Ok,
            DomainEventOpsApi.replayOutboxRecord(deps.domainEventService, DomainEventOutboxRecordId(recordId), actor, request)
          )
        }
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / recordId / "ack" =>
      support.handled {
        support.readJsonBody[AcknowledgeDomainEventOutboxRequest](req).flatMap { request =>
          val actor = support.principal(request.operator)
          support.jsonResponse(
            Status.Ok,
            DomainEventOpsApi.acknowledgeOutboxRecord(deps.domainEventService, DomainEventOutboxRecordId(recordId), actor, request)
          )
        }
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / recordId / "quarantine" =>
      support.handled {
        support.readJsonBody[QuarantineDomainEventOutboxRequest](req).flatMap { request =>
          val actor = support.principal(request.operator)
          support.jsonResponse(
            Status.Ok,
            DomainEventOpsApi.quarantineOutboxRecord(deps.domainEventService, DomainEventOutboxRecordId(recordId), actor, request)
          )
        }
      }

    case req @ GET -> Root / "admin" / "domain-events" / "subscribers" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val subscriberIdFilter = support.queryParam(req, "subscriberId").filter(_.nonEmpty)
        val subscribers = DomainEventOpsApi.subscriberStatuses(deps.domainEventQueryService, asOf, subscriberIdFilter)
        support.pagedJsonResponse(req, subscribers, support.activeFilters(req, "asOf", "subscriberId"))
      }

    case req @ GET -> Root / "admin" / "domain-events" / "subscribers" / subscriberId / "partitions" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val lagOnly = support.queryBooleanParam(req, "lagOnly").getOrElse(false)
        val blockedOnly = support.queryBooleanParam(req, "blockedOnly").getOrElse(false)
        val partitionKeyFilter = support.queryParam(req, "partitionKey").filter(_.nonEmpty)
        val partitions = DomainEventOpsApi.subscriberPartitionStatuses(
          deps.domainEventQueryService,
          subscriberId = subscriberId,
          asOf = asOf,
          lagOnly = lagOnly,
          blockedOnly = blockedOnly,
          partitionKey = partitionKeyFilter
        )
        support.pagedJsonResponse(
          req,
          partitions,
          support.activeFilters(req, "asOf", "lagOnly", "blockedOnly", "partitionKey")
        )
      }

    case req @ GET -> Root / "admin" / "event-cascade-records" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val query = EventCascadeRecordQuery(
          status = support.queryParam(req, "status").map(EventCascadeStatus.valueOf),
          consumer = support.queryParam(req, "consumer").map(EventCascadeConsumer.valueOf),
          eventType = support.queryParam(req, "eventType").filter(_.nonEmpty),
          aggregateType = support.queryParam(req, "aggregateType").filter(_.nonEmpty),
          aggregateId = support.queryParam(req, "aggregateId").filter(_.nonEmpty)
        )
        val records = DomainEventOpsApi.eventCascadeRecords(deps.tables, query)
        support.pagedJsonResponse(
          req,
          records,
          support.activeFilters(req, "status", "consumer", "eventType", "aggregateType", "aggregateId")
        )
      }
  }

