package riichinexus.microservices.opsanalytics.tables

import riichinexus.application.ports.{AdvancedStatsBoardRepository, AdvancedStatsRecomputeTaskRepository, AuditEventRepository, DashboardRepository, EventCascadeRecordRepository}
import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.objects.AuditTrailQuery

final class OpsAnalyticsTables(
    advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
    eventCascadeRecordRepository: EventCascadeRecordRepository,
    dashboardRepository: DashboardRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    auditEventRepository: AuditEventRepository
):
  def listAdvancedStatsTasks(): Vector[AdvancedStatsRecomputeTask] =
    advancedStatsRecomputeTaskRepository.findAll()

  def listEventCascadeRecords(): Vector[EventCascadeRecord] =
    eventCascadeRecordRepository.findAll()

  def findDashboard(owner: DashboardOwner): Option[Dashboard] =
    dashboardRepository.findByOwner(owner)

  def findAdvancedStatsBoard(owner: DashboardOwner): Option[AdvancedStatsBoard] =
    advancedStatsBoardRepository.findByOwner(owner)

  def listAuditEvents(query: AuditTrailQuery): Vector[AuditEventEntry] =
    auditEventRepository.findAll()
      .filter(entry => query.aggregateType.forall(_ == entry.aggregateType))
      .filter(entry => query.aggregateId.forall(_ == entry.aggregateId))
      .filter(entry => query.actorId.forall(entry.actorId.contains))
      .filter(entry => query.eventType.forall(_ == entry.eventType))
      .sortBy(entry => (entry.occurredAt, entry.id.value))

  def listAuditEventsByAggregate(
      aggregateType: String,
      aggregateId: String,
      query: AuditTrailQuery
  ): Vector[AuditEventEntry] =
    auditEventRepository.findByAggregate(aggregateType, aggregateId)
      .filter(entry => query.actorId.forall(entry.actorId.contains))
      .filter(entry => query.eventType.forall(_ == entry.eventType))
      .sortBy(entry => (entry.occurredAt, entry.id.value))

object OpsAnalyticsTables:
  val OwnedTables: Vector[String] = Vector(
    "advanced_stats_recompute_tasks",
    "event_cascade_records"
  )

  val ObservabilityTablesOrViews: Vector[String] = Vector(
    "domain_event_outbox",
    "domain_event_delivery_receipts",
    "domain_event_subscriber_cursors"
  )
