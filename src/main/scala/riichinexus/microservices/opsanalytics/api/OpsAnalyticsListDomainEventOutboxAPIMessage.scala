package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{DomainEventOutboxRecord as DomainEventOutboxRecordResponse}
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventOutboxQuery
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsDomainEventSubscriberQueries
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListDomainEventOutboxAPIMessage(
    query: DomainEventOutboxQuery
) extends APIMessage[PagedResponse[DomainEventOutboxRecordResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[DomainEventOutboxRecordResponse]] =
    for
      _ <- IO(requireOpsAdmin(context))
      asOf <- IO.realTimeInstant
      parsed <- IO(resolveQuery(asOf))
      records <- IO(listOutboxRecords(context, parsed))
    yield PagedResponse.fromItems(records, query.limit, query.offset, parsed.appliedFilters)(
      DomainEventOutboxRecordResponse.fromDomain
    )

  private def listOutboxRecords(
      context: ApiPlanContext,
      parsed: ResolvedOutboxQuery
  ): Vector[DomainEventOutboxRecord] =
    val module = context.support.opsAnalyticsModule
    val subscriber =
      parsed.subscriberId.map(OpsAnalyticsDomainEventSubscriberQueries.resolveSubscriber(module, _))
    val receiptsByOutboxAndSubscriber = module.domainEventDeliveryReceiptRepository.findAll()
      .groupBy(receipt => receipt.outboxRecordId -> receipt.subscriberId)

    module.domainEventOutboxRepository.findAll()
      .filter(record => query.status.forall(_ == record.status))
      .filter(record => parsed.eventType.forall(_ == record.eventType))
      .filter(record => parsed.aggregateType.forall(_ == record.aggregateType))
      .filter(record => parsed.aggregateId.forall(_ == record.aggregateId))
      .filter(record =>
        subscriber.forall(sub =>
          parsed.partitionKey.forall(_ == sub.partitionStrategy.partitionKey(record))
        )
      )
      .filter(record =>
        subscriber match
          case Some(sub) =>
            val hasReceipt = receiptsByOutboxAndSubscriber.contains(record.id -> sub.subscriberId)
            query.delivered.forall(_ == hasReceipt)
          case None =>
            query.delivered.isEmpty
      )
      .filter(record =>
        !parsed.blockedOnly ||
          subscriber.isEmpty ||
          OpsAnalyticsDomainEventSubscriberQueries.isBlockedForSubscriber(module, record, subscriber.get, parsed.asOf)
      )
      .sortBy(_.sequenceNo)

  private def resolveQuery(defaultAsOf: Instant): ResolvedOutboxQuery =
    val parsedSubscriberId = query.subscriberId.filter(_.nonEmpty)
    val parsedPartitionKey = query.partitionKey.filter(_.nonEmpty)
    val parsedBlockedOnly = query.blockedOnly.getOrElse(false)

    require(parsedSubscriberId.nonEmpty || query.delivered.isEmpty, "Input field delivered requires subscriberId")
    require(parsedSubscriberId.nonEmpty || parsedPartitionKey.isEmpty, "Input field partitionKey requires subscriberId")
    require(parsedSubscriberId.nonEmpty || !parsedBlockedOnly, "Input field blockedOnly requires subscriberId")

    ResolvedOutboxQuery(
      asOf = query.asOf.getOrElse(defaultAsOf),
      eventType = query.eventType.filter(_.nonEmpty),
      aggregateType = query.aggregateType.filter(_.nonEmpty),
      aggregateId = query.aggregateId.filter(_.nonEmpty),
      subscriberId = parsedSubscriberId,
      partitionKey = parsedPartitionKey,
      blockedOnly = parsedBlockedOnly,
      appliedFilters = Vector(
        query.asOf.map(value => "asOf" -> value.toString),
        query.status.map(value => "status" -> value.toString),
        query.eventType.filter(_.nonEmpty).map("eventType" -> _),
        query.aggregateType.filter(_.nonEmpty).map("aggregateType" -> _),
        query.aggregateId.filter(_.nonEmpty).map("aggregateId" -> _),
        query.subscriberId.filter(_.nonEmpty).map("subscriberId" -> _),
        query.partitionKey.filter(_.nonEmpty).map("partitionKey" -> _),
        query.delivered.map(value => "delivered" -> value.toString),
        query.blockedOnly.map(value => "blockedOnly" -> value.toString)
      ).flatten.toMap
    )

  private def requireOpsAdmin(context: ApiPlanContext): AccessPrincipal =
    val operator = context.support.principal(query.operatorId)
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator

  private final case class ResolvedOutboxQuery(
      asOf: Instant,
      eventType: Option[String],
      aggregateType: Option[String],
      aggregateId: Option[String],
      subscriberId: Option[String],
      partitionKey: Option[String],
      blockedOnly: Boolean,
      appliedFilters: Map[String, String]
  )
