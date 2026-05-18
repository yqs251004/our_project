package riichinexus.microservices.opsanalytics.api.messages

import java.time.Instant

import org.http4s.Status
import riichinexus.api.http.{ApiMessageContract, ApiMessageHandler, RouteSupport}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.api.*
import riichinexus.microservices.opsanalytics.api.requests.*
import riichinexus.microservices.opsanalytics.api.responses.*
import riichinexus.microservices.opsanalytics.api.responses.DemoScenarioResponses.given
import riichinexus.microservices.opsanalytics.api.responses.DomainEventResponses.given
import riichinexus.microservices.opsanalytics.api.responses.PerformanceResponses.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsTaskQuery, AuditTrailQuery, DomainEventOutboxQuery, EventCascadeRecordQuery}
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables
import riichinexus.microservices.shared.api.responses.PagedResponse
import upickle.default.*

object OpsAnalyticsApiMessages:
  final case class OpsAnalyticsPlayerDashboardApiMessageInput(playerId: String, operatorId: String) derives CanEqual
  object OpsAnalyticsPlayerDashboardApiMessageInput:
    given ReadWriter[OpsAnalyticsPlayerDashboardApiMessageInput] = macroRW

  final case class OpsAnalyticsClubDashboardApiMessageInput(clubId: String, operatorId: String) derives CanEqual
  object OpsAnalyticsClubDashboardApiMessageInput:
    given ReadWriter[OpsAnalyticsClubDashboardApiMessageInput] = macroRW

  final case class OpsAnalyticsPlayerAdvancedStatsApiMessageInput(playerId: String, operatorId: String) derives CanEqual
  object OpsAnalyticsPlayerAdvancedStatsApiMessageInput:
    given ReadWriter[OpsAnalyticsPlayerAdvancedStatsApiMessageInput] = macroRW

  final case class OpsAnalyticsClubAdvancedStatsApiMessageInput(clubId: String, operatorId: String) derives CanEqual
  object OpsAnalyticsClubAdvancedStatsApiMessageInput:
    given ReadWriter[OpsAnalyticsClubAdvancedStatsApiMessageInput] = macroRW

  final case class OpsAnalyticsListAuditsApiMessageInput(
      operatorId: String,
      aggregateType: Option[String] = None,
      aggregateId: Option[String] = None,
      actorId: Option[String] = None,
      eventType: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object OpsAnalyticsListAuditsApiMessageInput:
    given ReadWriter[OpsAnalyticsListAuditsApiMessageInput] = macroRW

  final case class OpsAnalyticsListAggregateAuditsApiMessageInput(
      operatorId: String,
      aggregateType: String,
      aggregateId: String,
      actorId: Option[String] = None,
      eventType: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object OpsAnalyticsListAggregateAuditsApiMessageInput:
    given ReadWriter[OpsAnalyticsListAggregateAuditsApiMessageInput] = macroRW

  final case class OpsAnalyticsListAdvancedStatsTasksApiMessageInput(operatorId: String, status: Option[String] = None, limit: Option[Int] = None, offset: Option[Int] = None) derives CanEqual
  object OpsAnalyticsListAdvancedStatsTasksApiMessageInput:
    given ReadWriter[OpsAnalyticsListAdvancedStatsTasksApiMessageInput] = macroRW

  final case class OpsAnalyticsAsOfApiMessageInput(operatorId: String, asOf: Option[String] = None) derives CanEqual
  object OpsAnalyticsAsOfApiMessageInput:
    given ReadWriter[OpsAnalyticsAsOfApiMessageInput] = macroRW

  final case class OpsAnalyticsPerformanceSummaryApiMessageInput(operatorId: String, limit: Option[Int] = None) derives CanEqual
  object OpsAnalyticsPerformanceSummaryApiMessageInput:
    given ReadWriter[OpsAnalyticsPerformanceSummaryApiMessageInput] = macroRW

  final case class OpsAnalyticsDomainEventOutboxListApiMessageInput(
      operatorId: String,
      asOf: Option[String] = None,
      status: Option[String] = None,
      eventType: Option[String] = None,
      aggregateType: Option[String] = None,
      aggregateId: Option[String] = None,
      subscriberId: Option[String] = None,
      partitionKey: Option[String] = None,
      delivered: Option[Boolean] = None,
      blockedOnly: Option[Boolean] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object OpsAnalyticsDomainEventOutboxListApiMessageInput:
    given ReadWriter[OpsAnalyticsDomainEventOutboxListApiMessageInput] = macroRW

  final case class OpsAnalyticsDomainEventOutboxRecordApiMessageInput(operatorId: String, recordId: String) derives CanEqual
  object OpsAnalyticsDomainEventOutboxRecordApiMessageInput:
    given ReadWriter[OpsAnalyticsDomainEventOutboxRecordApiMessageInput] = macroRW

  final case class OpsAnalyticsReplayDomainEventOutboxRecordApiMessageInput(recordId: String, request: ReplayDomainEventOutboxRequest) derives CanEqual
  object OpsAnalyticsReplayDomainEventOutboxRecordApiMessageInput:
    given ReadWriter[OpsAnalyticsReplayDomainEventOutboxRecordApiMessageInput] = macroRW

  final case class OpsAnalyticsAcknowledgeDomainEventOutboxRecordApiMessageInput(recordId: String, request: AcknowledgeDomainEventOutboxRequest) derives CanEqual
  object OpsAnalyticsAcknowledgeDomainEventOutboxRecordApiMessageInput:
    given ReadWriter[OpsAnalyticsAcknowledgeDomainEventOutboxRecordApiMessageInput] = macroRW

  final case class OpsAnalyticsQuarantineDomainEventOutboxRecordApiMessageInput(recordId: String, request: QuarantineDomainEventOutboxRequest) derives CanEqual
  object OpsAnalyticsQuarantineDomainEventOutboxRecordApiMessageInput:
    given ReadWriter[OpsAnalyticsQuarantineDomainEventOutboxRecordApiMessageInput] = macroRW

  final case class OpsAnalyticsListDomainEventSubscribersApiMessageInput(operatorId: String, asOf: Option[String] = None, subscriberId: Option[String] = None, limit: Option[Int] = None, offset: Option[Int] = None) derives CanEqual
  object OpsAnalyticsListDomainEventSubscribersApiMessageInput:
    given ReadWriter[OpsAnalyticsListDomainEventSubscribersApiMessageInput] = macroRW

  final case class OpsAnalyticsListDomainEventSubscriberPartitionsApiMessageInput(
      operatorId: String,
      subscriberId: String,
      asOf: Option[String] = None,
      lagOnly: Option[Boolean] = None,
      blockedOnly: Option[Boolean] = None,
      partitionKey: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object OpsAnalyticsListDomainEventSubscriberPartitionsApiMessageInput:
    given ReadWriter[OpsAnalyticsListDomainEventSubscriberPartitionsApiMessageInput] = macroRW

  final case class OpsAnalyticsListEventCascadeRecordsApiMessageInput(
      operatorId: String,
      status: Option[String] = None,
      consumer: Option[String] = None,
      eventType: Option[String] = None,
      aggregateType: Option[String] = None,
      aggregateId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives CanEqual
  object OpsAnalyticsListEventCascadeRecordsApiMessageInput:
    given ReadWriter[OpsAnalyticsListEventCascadeRecordsApiMessageInput] = macroRW

  final case class OpsAnalyticsDemoScenarioApiMessageInput(
      variant: Option[String] = None,
      bootstrapIfMissing: Option[Boolean] = None,
      refreshDerived: Option[Boolean] = None
  ) derives CanEqual
  object OpsAnalyticsDemoScenarioApiMessageInput:
    given ReadWriter[OpsAnalyticsDemoScenarioApiMessageInput] = macroRW

  final case class OpsAnalyticsDemoActionApiMessageInput(variant: Option[String] = None, action: String, bootstrapIfMissing: Option[Boolean] = None) derives CanEqual
  object OpsAnalyticsDemoActionApiMessageInput:
    given ReadWriter[OpsAnalyticsDemoActionApiMessageInput] = macroRW

  private final case class Dependencies(
      tables: OpsAnalyticsTables,
      advancedStatsService: AdvancedStatsPipelineService,
      domainEventQueryService: DomainEventQueryService,
      domainEventService: DomainEventOperationsService,
      performanceDiagnosticsService: PerformanceDiagnosticsService,
      demoScenarioService: DemoScenarioService
  )

  private def dependencies(support: RouteSupport): Dependencies =
    val module = support.opsAnalyticsModule
    Dependencies(
      tables = module.tables,
      advancedStatsService = module.advancedStatsService,
      domainEventQueryService = module.domainEventQueryService,
      domainEventService = module.domainEventService,
      performanceDiagnosticsService = module.performanceDiagnosticsService,
      demoScenarioService = module.demoScenarioService
    )

  private def pagedJsonResponse[T: Writer](
      support: RouteSupport,
      items: Vector[T],
      limit: Option[Int],
      offset: Option[Int],
      appliedFilters: Map[String, String]
  ) =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    support.jsonResponse(Status.Ok, PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters))

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap

  private def asOf(value: Option[String]): Instant =
    value.filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())

  private def requireOpsAdmin(support: RouteSupport, operatorId: String): AccessPrincipal =
    val operator = support.principal(PlayerId(operatorId))
    support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator

  private def demoVariant(support: RouteSupport, value: Option[String]): DemoScenarioVariant =
    value.filter(_.nonEmpty).map(support.parseEnum("variant", _)(DemoScenarioVariant.valueOf)).getOrElse(DemoScenarioVariant.Basic)

  val opsAnalyticsPlayerDashboardApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsPlayerDashboardApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsPlayerDashboardApiMessageInput](req).flatMap { request =>
        val targetPlayerId = PlayerId(request.playerId)
        val operator = support.principal(PlayerId(request.operatorId))
        support.requirePermission(operator, Permission.ViewOwnDashboard, subjectPlayerId = Some(targetPlayerId))
        support.optionJsonResponse(DashboardApi.playerDashboard(deps.tables, targetPlayerId))
      }
  )

  val opsAnalyticsClubDashboardApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsClubDashboardApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsClubDashboardApiMessageInput](req).flatMap { request =>
        val targetClubId = ClubId(request.clubId)
        val operator = support.principal(PlayerId(request.operatorId))
        support.requirePermission(operator, Permission.ViewClubDashboard, clubId = Some(targetClubId))
        support.optionJsonResponse(DashboardApi.clubDashboard(deps.tables, targetClubId))
      }
  )

  val opsAnalyticsPlayerAdvancedStatsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsPlayerAdvancedStatsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsPlayerAdvancedStatsApiMessageInput](req).flatMap { request =>
        val targetPlayerId = PlayerId(request.playerId)
        val operator = support.principal(PlayerId(request.operatorId))
        support.requirePermission(operator, Permission.ViewOwnDashboard, subjectPlayerId = Some(targetPlayerId))
        support.optionJsonResponse(DashboardApi.playerAdvancedStats(deps.tables, targetPlayerId))
      }
  )

  val opsAnalyticsClubAdvancedStatsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsClubAdvancedStatsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsClubAdvancedStatsApiMessageInput](req).flatMap { request =>
        val targetClubId = ClubId(request.clubId)
        val operator = support.principal(PlayerId(request.operatorId))
        support.requirePermission(operator, Permission.ViewClubDashboard, clubId = Some(targetClubId))
        support.optionJsonResponse(DashboardApi.clubAdvancedStats(deps.tables, targetClubId))
      }
  )

  val opsAnalyticsListAuditsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsListAuditsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsListAuditsApiMessageInput](req).flatMap { request =>
        val operator = support.principal(PlayerId(request.operatorId))
        support.requirePermission(operator, Permission.ViewAuditTrail)
        val query = AuditTrailQuery(request.aggregateType.filter(_.nonEmpty), request.aggregateId.filter(_.nonEmpty), request.actorId.filter(_.nonEmpty).map(PlayerId(_)), request.eventType.filter(_.nonEmpty))
        val audits = AuditTrailApi.listAudits(deps.tables, query)
        pagedJsonResponse(support, audits, request.limit, request.offset, filters(request.aggregateType.filter(_.nonEmpty).map("aggregateType" -> _), request.aggregateId.filter(_.nonEmpty).map("aggregateId" -> _), request.actorId.filter(_.nonEmpty).map("actorId" -> _), request.eventType.filter(_.nonEmpty).map("eventType" -> _), Some("operatorId" -> request.operatorId)))
      }
  )

  val opsAnalyticsListAggregateAuditsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsListAggregateAuditsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsListAggregateAuditsApiMessageInput](req).flatMap { request =>
        val operator = support.principal(PlayerId(request.operatorId))
        support.requirePermission(operator, Permission.ViewAuditTrail)
        val query = AuditTrailQuery(actorId = request.actorId.filter(_.nonEmpty).map(PlayerId(_)), eventType = request.eventType.filter(_.nonEmpty))
        val audits = AuditTrailApi.listAuditsByAggregate(deps.tables, request.aggregateType, request.aggregateId, query)
        pagedJsonResponse(support, audits, request.limit, request.offset, filters(Some("aggregateType" -> request.aggregateType), Some("aggregateId" -> request.aggregateId), request.actorId.filter(_.nonEmpty).map("actorId" -> _), request.eventType.filter(_.nonEmpty).map("eventType" -> _), Some("operatorId" -> request.operatorId)))
      }
  )

  val opsAnalyticsListAdvancedStatsTasksApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsListAdvancedStatsTasksApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsListAdvancedStatsTasksApiMessageInput](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        val query = AdvancedStatsTaskQuery(request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(AdvancedStatsRecomputeTaskStatus.valueOf)))
        val tasks = AdvancedStatsApi.listTasks(deps.tables, query)
        pagedJsonResponse(support, tasks, request.limit, request.offset, filters(request.status.filter(_.nonEmpty).map("status" -> _)))
      }
  )

  val opsAnalyticsAdvancedStatsSummaryApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsAdvancedStatsSummaryApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsAsOfApiMessageInput](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        support.jsonResponse(Status.Ok, AdvancedStatsApi.taskQueueSummary(deps.advancedStatsService, asOf(request.asOf)))
      }
  )

  val opsAnalyticsRecomputeAdvancedStatsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsRecomputeAdvancedStatsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[RecomputeAdvancedStatsRequest](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        support.jsonResponse(Status.Accepted, AdvancedStatsApi.recompute(deps.advancedStatsService, request, Instant.now()))
      }
  )

  val opsAnalyticsProcessAdvancedStatsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsProcessAdvancedStatsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[ProcessAdvancedStatsTasksRequest](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        support.jsonResponse(Status.Ok, AdvancedStatsApi.processPending(deps.advancedStatsService, request, Instant.now()))
      }
  )

  val opsAnalyticsPerformanceSummaryApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsPerformanceSummaryApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsPerformanceSummaryApiMessageInput](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        val limit = request.limit.getOrElse(15)
        require(limit > 0, "Input field limit must be positive")
        support.jsonResponse(Status.Ok, PerformanceApi.snapshot(deps.performanceDiagnosticsService, limit))
      }
  )

  val opsAnalyticsDomainEventsSummaryApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDomainEventsSummaryApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsAsOfApiMessageInput](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        support.jsonResponse(Status.Ok, DomainEventOpsApi.summary(deps.domainEventQueryService, asOf(request.asOf)))
      }
  )

  val opsAnalyticsListDomainEventOutboxApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsListDomainEventOutboxApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDomainEventOutboxListApiMessageInput](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        val query = DomainEventOutboxQuery(
          asOf = asOf(request.asOf),
          status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(DomainEventOutboxStatus.valueOf)),
          eventType = request.eventType.filter(_.nonEmpty),
          aggregateType = request.aggregateType.filter(_.nonEmpty),
          aggregateId = request.aggregateId.filter(_.nonEmpty),
          subscriberId = request.subscriberId.filter(_.nonEmpty),
          partitionKey = request.partitionKey.filter(_.nonEmpty),
          delivered = request.delivered,
          blockedOnly = request.blockedOnly.getOrElse(false)
        )
        require(query.subscriberId.nonEmpty || query.delivered.isEmpty, "Input field delivered requires subscriberId")
        require(query.subscriberId.nonEmpty || query.partitionKey.isEmpty, "Input field partitionKey requires subscriberId")
        require(query.subscriberId.nonEmpty || !query.blockedOnly, "Input field blockedOnly requires subscriberId")
        val records = DomainEventOpsApi.outboxRecords(deps.domainEventQueryService, query)
        pagedJsonResponse(support, records, request.limit, request.offset, filters(request.asOf.filter(_.nonEmpty).map("asOf" -> _), request.status.filter(_.nonEmpty).map("status" -> _), request.eventType.filter(_.nonEmpty).map("eventType" -> _), request.aggregateType.filter(_.nonEmpty).map("aggregateType" -> _), request.aggregateId.filter(_.nonEmpty).map("aggregateId" -> _), request.subscriberId.filter(_.nonEmpty).map("subscriberId" -> _), request.partitionKey.filter(_.nonEmpty).map("partitionKey" -> _), request.delivered.map(value => "delivered" -> value.toString), request.blockedOnly.map(value => "blockedOnly" -> value.toString)))
      }
  )

  val opsAnalyticsReplayDomainEventOutboxApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsReplayDomainEventOutboxApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[BatchReplayDomainEventOutboxRequest](req).flatMap { request =>
        support.jsonResponse(Status.Ok, DomainEventOpsApi.replayOutboxRecords(deps.domainEventService, support.principal(request.operator), request))
      }
  )

  val opsAnalyticsAcknowledgeDomainEventOutboxApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsAcknowledgeDomainEventOutboxApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[BatchAcknowledgeDomainEventOutboxRequest](req).flatMap { request =>
        support.jsonResponse(Status.Ok, DomainEventOpsApi.acknowledgeOutboxRecords(deps.domainEventService, support.principal(request.operator), request))
      }
  )

  val opsAnalyticsQuarantineDomainEventOutboxApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsQuarantineDomainEventOutboxApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[BatchQuarantineDomainEventOutboxRequest](req).flatMap { request =>
        support.jsonResponse(Status.Ok, DomainEventOpsApi.quarantineOutboxRecords(deps.domainEventService, support.principal(request.operator), request))
      }
  )

  val opsAnalyticsDomainEventOutboxHistoryApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDomainEventOutboxHistoryApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDomainEventOutboxRecordApiMessageInput](req).flatMap { request =>
        support.jsonResponse(Status.Ok, DomainEventOpsApi.outboxHistory(deps.domainEventQueryService, DomainEventOutboxRecordId(request.recordId), support.principal(PlayerId(request.operatorId))))
      }
  )

  val opsAnalyticsReplayDomainEventOutboxRecordApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsReplayDomainEventOutboxRecordApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsReplayDomainEventOutboxRecordApiMessageInput](req).flatMap { input =>
        support.jsonResponse(Status.Ok, DomainEventOpsApi.replayOutboxRecord(deps.domainEventService, DomainEventOutboxRecordId(input.recordId), support.principal(input.request.operator), input.request))
      }
  )

  val opsAnalyticsAcknowledgeDomainEventOutboxRecordApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsAcknowledgeDomainEventOutboxRecordApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsAcknowledgeDomainEventOutboxRecordApiMessageInput](req).flatMap { input =>
        support.jsonResponse(Status.Ok, DomainEventOpsApi.acknowledgeOutboxRecord(deps.domainEventService, DomainEventOutboxRecordId(input.recordId), support.principal(input.request.operator), input.request))
      }
  )

  val opsAnalyticsQuarantineDomainEventOutboxRecordApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsQuarantineDomainEventOutboxRecordApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsQuarantineDomainEventOutboxRecordApiMessageInput](req).flatMap { input =>
        support.jsonResponse(Status.Ok, DomainEventOpsApi.quarantineOutboxRecord(deps.domainEventService, DomainEventOutboxRecordId(input.recordId), support.principal(input.request.operator), input.request))
      }
  )

  val opsAnalyticsListDomainEventSubscribersApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsListDomainEventSubscribersApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsListDomainEventSubscribersApiMessageInput](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        val subscribers = DomainEventOpsApi.subscriberStatuses(deps.domainEventQueryService, asOf(request.asOf), request.subscriberId.filter(_.nonEmpty))
        pagedJsonResponse(support, subscribers, request.limit, request.offset, filters(request.asOf.filter(_.nonEmpty).map("asOf" -> _), request.subscriberId.filter(_.nonEmpty).map("subscriberId" -> _)))
      }
  )

  val opsAnalyticsListDomainEventSubscriberPartitionsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsListDomainEventSubscriberPartitionsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsListDomainEventSubscriberPartitionsApiMessageInput](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        val partitions = DomainEventOpsApi.subscriberPartitionStatuses(deps.domainEventQueryService, request.subscriberId, asOf(request.asOf), request.lagOnly.getOrElse(false), request.blockedOnly.getOrElse(false), request.partitionKey.filter(_.nonEmpty))
        pagedJsonResponse(support, partitions, request.limit, request.offset, filters(request.asOf.filter(_.nonEmpty).map("asOf" -> _), request.lagOnly.map(value => "lagOnly" -> value.toString), request.blockedOnly.map(value => "blockedOnly" -> value.toString), request.partitionKey.filter(_.nonEmpty).map("partitionKey" -> _)))
      }
  )

  val opsAnalyticsListEventCascadeRecordsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsListEventCascadeRecordsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsListEventCascadeRecordsApiMessageInput](req).flatMap { request =>
        requireOpsAdmin(support, request.operatorId)
        val query = EventCascadeRecordQuery(
          status = request.status.filter(_.nonEmpty).map(support.parseEnum("status", _)(EventCascadeStatus.valueOf)),
          consumer = request.consumer.filter(_.nonEmpty).map(support.parseEnum("consumer", _)(EventCascadeConsumer.valueOf)),
          eventType = request.eventType.filter(_.nonEmpty),
          aggregateType = request.aggregateType.filter(_.nonEmpty),
          aggregateId = request.aggregateId.filter(_.nonEmpty)
        )
        val records = DomainEventOpsApi.eventCascadeRecords(deps.tables, query)
        pagedJsonResponse(support, records, request.limit, request.offset, filters(request.status.filter(_.nonEmpty).map("status" -> _), request.consumer.filter(_.nonEmpty).map("consumer" -> _), request.eventType.filter(_.nonEmpty).map("eventType" -> _), request.aggregateType.filter(_.nonEmpty).map("aggregateType" -> _), request.aggregateId.filter(_.nonEmpty).map("aggregateId" -> _)))
      }
  )

  val opsAnalyticsDemoSummaryApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDemoSummaryApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDemoScenarioApiMessageInput](req).flatMap { request =>
        val variant = demoVariant(support, request.variant)
        val refreshDerived = request.refreshDerived.getOrElse(true)
        val summary = deps.demoScenarioService.currentScenario(variant, refreshDerived).orElse(if request.bootstrapIfMissing.getOrElse(false) then Some(deps.demoScenarioService.bootstrapScenario(variant, refreshDerived)) else None)
        support.optionJsonResponse(summary)
      }
  )

  val opsAnalyticsDemoReadinessApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDemoReadinessApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDemoScenarioApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(deps.demoScenarioService.currentReadiness(demoVariant(support, request.variant), request.bootstrapIfMissing.getOrElse(false), request.refreshDerived.getOrElse(true)))
      }
  )

  val opsAnalyticsDemoGuideApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDemoGuideApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDemoScenarioApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(deps.demoScenarioService.guide(demoVariant(support, request.variant), request.bootstrapIfMissing.getOrElse(true), request.refreshDerived.getOrElse(true)))
      }
  )

  val opsAnalyticsDemoWidgetsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDemoWidgetsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDemoScenarioApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(deps.demoScenarioService.widgets(demoVariant(support, request.variant), request.bootstrapIfMissing.getOrElse(true), request.refreshDerived.getOrElse(true)))
      }
  )

  val opsAnalyticsDemoActionsApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDemoActionsApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDemoScenarioApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(deps.demoScenarioService.actionCatalog(demoVariant(support, request.variant), request.bootstrapIfMissing.getOrElse(true), request.refreshDerived.getOrElse(true)))
      }
  )

  val opsAnalyticsDemoExecuteActionApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDemoExecuteActionApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDemoActionApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(deps.demoScenarioService.executeAction(demoVariant(support, request.variant), support.parseEnum("action", request.action)(DemoScenarioActionCode.valueOf), request.bootstrapIfMissing.getOrElse(true)))
      }
  )

  val opsAnalyticsDemoBootstrapApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDemoBootstrapApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDemoScenarioApiMessageInput](req).flatMap { request =>
        support.jsonResponse(Status.Ok, deps.demoScenarioService.bootstrapScenario(demoVariant(support, request.variant), request.refreshDerived.getOrElse(true)))
      }
  )

  val opsAnalyticsDemoRefreshApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDemoRefreshApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDemoScenarioApiMessageInput](req).flatMap { request =>
        support.optionJsonResponse(deps.demoScenarioService.refreshScenario(demoVariant(support, request.variant), request.bootstrapIfMissing.getOrElse(true)))
      }
  )

  val opsAnalyticsDemoResetApiMessage: ApiMessageHandler = ApiMessageHandler(
    "opsAnalyticsDemoResetApiMessage",
    (support, req) =>
      val deps = dependencies(support)
      support.readJsonBody[OpsAnalyticsDemoScenarioApiMessageInput](req).flatMap { request =>
        support.jsonResponse(Status.Ok, deps.demoScenarioService.bootstrapScenario(demoVariant(support, request.variant), request.refreshDerived.getOrElse(true)))
      }
  )

  val handlers: Vector[ApiMessageHandler] = Vector(
    opsAnalyticsPlayerDashboardApiMessage,
    opsAnalyticsClubDashboardApiMessage,
    opsAnalyticsPlayerAdvancedStatsApiMessage,
    opsAnalyticsClubAdvancedStatsApiMessage,
    opsAnalyticsListAuditsApiMessage,
    opsAnalyticsListAggregateAuditsApiMessage,
    opsAnalyticsListAdvancedStatsTasksApiMessage,
    opsAnalyticsAdvancedStatsSummaryApiMessage,
    opsAnalyticsRecomputeAdvancedStatsApiMessage,
    opsAnalyticsProcessAdvancedStatsApiMessage,
    opsAnalyticsPerformanceSummaryApiMessage,
    opsAnalyticsDomainEventsSummaryApiMessage,
    opsAnalyticsListDomainEventOutboxApiMessage,
    opsAnalyticsReplayDomainEventOutboxApiMessage,
    opsAnalyticsAcknowledgeDomainEventOutboxApiMessage,
    opsAnalyticsQuarantineDomainEventOutboxApiMessage,
    opsAnalyticsDomainEventOutboxHistoryApiMessage,
    opsAnalyticsReplayDomainEventOutboxRecordApiMessage,
    opsAnalyticsAcknowledgeDomainEventOutboxRecordApiMessage,
    opsAnalyticsQuarantineDomainEventOutboxRecordApiMessage,
    opsAnalyticsListDomainEventSubscribersApiMessage,
    opsAnalyticsListDomainEventSubscriberPartitionsApiMessage,
    opsAnalyticsListEventCascadeRecordsApiMessage,
    opsAnalyticsDemoSummaryApiMessage,
    opsAnalyticsDemoReadinessApiMessage,
    opsAnalyticsDemoGuideApiMessage,
    opsAnalyticsDemoWidgetsApiMessage,
    opsAnalyticsDemoActionsApiMessage,
    opsAnalyticsDemoExecuteActionApiMessage,
    opsAnalyticsDemoBootstrapApiMessage,
    opsAnalyticsDemoRefreshApiMessage,
    opsAnalyticsDemoResetApiMessage
  )

  val contracts: Vector[ApiMessageContract] = Vector(
    ApiMessageContract("opsAnalyticsPlayerDashboardApiMessage", "OpsAnalyticsPlayerDashboardApiMessageInput", "Dashboard", "opsanalytics", "GET /dashboards/players/{playerId}", "done"),
    ApiMessageContract("opsAnalyticsClubDashboardApiMessage", "OpsAnalyticsClubDashboardApiMessageInput", "Dashboard", "opsanalytics", "GET /dashboards/clubs/{clubId}", "done"),
    ApiMessageContract("opsAnalyticsPlayerAdvancedStatsApiMessage", "OpsAnalyticsPlayerAdvancedStatsApiMessageInput", "AdvancedStatsBoard", "opsanalytics", "GET /advanced-stats/players/{playerId}", "done"),
    ApiMessageContract("opsAnalyticsClubAdvancedStatsApiMessage", "OpsAnalyticsClubAdvancedStatsApiMessageInput", "AdvancedStatsBoard", "opsanalytics", "GET /advanced-stats/clubs/{clubId}", "done"),
    ApiMessageContract("opsAnalyticsListAuditsApiMessage", "OpsAnalyticsListAuditsApiMessageInput", "PagedResponse[AuditEventEntry]", "opsanalytics", "GET /audits", "done"),
    ApiMessageContract("opsAnalyticsListAggregateAuditsApiMessage", "OpsAnalyticsListAggregateAuditsApiMessageInput", "PagedResponse[AuditEventEntry]", "opsanalytics", "GET /audits/{aggregateType}/{aggregateId}", "done"),
    ApiMessageContract("opsAnalyticsListAdvancedStatsTasksApiMessage", "OpsAnalyticsListAdvancedStatsTasksApiMessageInput", "PagedResponse[AdvancedStatsRecomputeTask]", "opsanalytics", "GET /admin/advanced-stats/tasks", "done"),
    ApiMessageContract("opsAnalyticsAdvancedStatsSummaryApiMessage", "OpsAnalyticsAsOfApiMessageInput", "AdvancedStatsTaskQueueSummary", "opsanalytics", "GET /admin/advanced-stats/summary", "done"),
    ApiMessageContract("opsAnalyticsRecomputeAdvancedStatsApiMessage", "RecomputeAdvancedStatsRequest", "Vector[AdvancedStatsRecomputeTask]", "opsanalytics", "POST /admin/advanced-stats/recompute", "done"),
    ApiMessageContract("opsAnalyticsProcessAdvancedStatsApiMessage", "ProcessAdvancedStatsTasksRequest", "Vector[AdvancedStatsRecomputeTask]", "opsanalytics", "POST /admin/advanced-stats/process", "done"),
    ApiMessageContract("opsAnalyticsPerformanceSummaryApiMessage", "OpsAnalyticsPerformanceSummaryApiMessageInput", "PerformanceSummaryResponse", "opsanalytics", "GET /admin/performance/summary", "done"),
    ApiMessageContract("opsAnalyticsDomainEventsSummaryApiMessage", "OpsAnalyticsAsOfApiMessageInput", "DomainEventBusSummaryResponse", "opsanalytics", "GET /admin/domain-events/summary", "done"),
    ApiMessageContract("opsAnalyticsListDomainEventOutboxApiMessage", "OpsAnalyticsDomainEventOutboxListApiMessageInput", "PagedResponse[DomainEventOutboxRecordResponse]", "opsanalytics", "GET /admin/domain-events/outbox", "done"),
    ApiMessageContract("opsAnalyticsReplayDomainEventOutboxApiMessage", "BatchReplayDomainEventOutboxRequest", "DomainEventBatchOperationResponse", "opsanalytics", "POST /admin/domain-events/outbox/replay", "done"),
    ApiMessageContract("opsAnalyticsAcknowledgeDomainEventOutboxApiMessage", "BatchAcknowledgeDomainEventOutboxRequest", "DomainEventBatchOperationResponse", "opsanalytics", "POST /admin/domain-events/outbox/ack", "done"),
    ApiMessageContract("opsAnalyticsQuarantineDomainEventOutboxApiMessage", "BatchQuarantineDomainEventOutboxRequest", "DomainEventBatchOperationResponse", "opsanalytics", "POST /admin/domain-events/outbox/quarantine", "done"),
    ApiMessageContract("opsAnalyticsDomainEventOutboxHistoryApiMessage", "OpsAnalyticsDomainEventOutboxRecordApiMessageInput", "DomainEventOutboxHistoryResponse", "opsanalytics", "GET /admin/domain-events/outbox/{recordId}/history", "done"),
    ApiMessageContract("opsAnalyticsReplayDomainEventOutboxRecordApiMessage", "OpsAnalyticsReplayDomainEventOutboxRecordApiMessageInput", "DomainEventOutboxRecordResponse", "opsanalytics", "POST /admin/domain-events/outbox/{recordId}/replay", "done"),
    ApiMessageContract("opsAnalyticsAcknowledgeDomainEventOutboxRecordApiMessage", "OpsAnalyticsAcknowledgeDomainEventOutboxRecordApiMessageInput", "DomainEventOutboxRecordResponse", "opsanalytics", "POST /admin/domain-events/outbox/{recordId}/ack", "done"),
    ApiMessageContract("opsAnalyticsQuarantineDomainEventOutboxRecordApiMessage", "OpsAnalyticsQuarantineDomainEventOutboxRecordApiMessageInput", "DomainEventOutboxRecordResponse", "opsanalytics", "POST /admin/domain-events/outbox/{recordId}/quarantine", "done"),
    ApiMessageContract("opsAnalyticsListDomainEventSubscribersApiMessage", "OpsAnalyticsListDomainEventSubscribersApiMessageInput", "PagedResponse[DomainEventSubscriberStatusResponse]", "opsanalytics", "GET /admin/domain-events/subscribers", "done"),
    ApiMessageContract("opsAnalyticsListDomainEventSubscriberPartitionsApiMessage", "OpsAnalyticsListDomainEventSubscriberPartitionsApiMessageInput", "PagedResponse[DomainEventSubscriberPartitionStatusResponse]", "opsanalytics", "GET /admin/domain-events/subscribers/{subscriberId}/partitions", "done"),
    ApiMessageContract("opsAnalyticsListEventCascadeRecordsApiMessage", "OpsAnalyticsListEventCascadeRecordsApiMessageInput", "PagedResponse[EventCascadeRecordResponse]", "opsanalytics", "GET /admin/event-cascade-records", "done"),
    ApiMessageContract("opsAnalyticsDemoSummaryApiMessage", "OpsAnalyticsDemoScenarioApiMessageInput", "DemoScenarioSnapshot", "opsanalytics", "GET /demo/summary", "done"),
    ApiMessageContract("opsAnalyticsDemoReadinessApiMessage", "OpsAnalyticsDemoScenarioApiMessageInput", "DemoScenarioReadiness", "opsanalytics", "GET /demo/readiness", "done"),
    ApiMessageContract("opsAnalyticsDemoGuideApiMessage", "OpsAnalyticsDemoScenarioApiMessageInput", "DemoScenarioGuide", "opsanalytics", "GET /demo/guide", "done"),
    ApiMessageContract("opsAnalyticsDemoWidgetsApiMessage", "OpsAnalyticsDemoScenarioApiMessageInput", "DemoScenarioWidgets", "opsanalytics", "GET /demo/widgets", "done"),
    ApiMessageContract("opsAnalyticsDemoActionsApiMessage", "OpsAnalyticsDemoScenarioApiMessageInput", "Vector[DemoScenarioActionSpec]", "opsanalytics", "GET /demo/actions", "done"),
    ApiMessageContract("opsAnalyticsDemoExecuteActionApiMessage", "OpsAnalyticsDemoActionApiMessageInput", "DemoScenarioActionResult", "opsanalytics", "POST /demo/actions/{actionCode}", "done"),
    ApiMessageContract("opsAnalyticsDemoBootstrapApiMessage", "OpsAnalyticsDemoScenarioApiMessageInput", "DemoScenarioSnapshot", "opsanalytics", "POST /demo/bootstrap", "done"),
    ApiMessageContract("opsAnalyticsDemoRefreshApiMessage", "OpsAnalyticsDemoScenarioApiMessageInput", "DemoScenarioSnapshot", "opsanalytics", "POST /demo/refresh", "done"),
    ApiMessageContract("opsAnalyticsDemoResetApiMessage", "OpsAnalyticsDemoScenarioApiMessageInput", "DemoScenarioSnapshot", "opsanalytics", "POST /demo/reset", "done")
  )
