package riichinexus.microservices.opsanalytics.api

import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.objects.AuditTrailQuery
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables

object AuditTrailApi:

  def listAudits(
      tables: OpsAnalyticsTables,
      query: AuditTrailQuery
  ): Vector[AuditEventEntry] =
    tables.listAuditEvents(query)

  def listAuditsByAggregate(
      tables: OpsAnalyticsTables,
      aggregateType: String,
      aggregateId: String,
      query: AuditTrailQuery
  ): Vector[AuditEventEntry] =
    tables.listAuditEventsByAggregate(
      aggregateType = aggregateType,
      aggregateId = aggregateId,
      query = query
    )
