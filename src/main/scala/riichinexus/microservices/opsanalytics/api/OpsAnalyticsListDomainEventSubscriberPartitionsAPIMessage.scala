package riichinexus.microservices.opsanalytics.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.ports.DomainEventSubscriber
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListDomainEventSubscriberPartitionsAPIMessage(
    operatorId: PlayerId,
    subscriberId: String,
    asOf: Option[Instant] = None,
    lagOnly: Option[Boolean] = None,
    blockedOnly: Option[Boolean] = None,
    partitionKey: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[DomainEventSubscriberPartitionStatus]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[DomainEventSubscriberPartitionStatus]] =
    IO {
      requireOpsAdmin(context)
      val partitions = subscriberPartitionStatuses(
        context = context,
        subscriberId = subscriberId,
        asOf = asOf.getOrElse(Instant.now()),
        lagOnly = lagOnly.getOrElse(false),
        blockedOnly = blockedOnly.getOrElse(false),
        partitionKey = partitionKey.filter(_.nonEmpty)
      )
      paged(
        partitions,
        Vector(
          asOf.map(value => "asOf" -> value.toString),
          lagOnly.map(value => "lagOnly" -> value.toString),
          blockedOnly.map(value => "blockedOnly" -> value.toString),
          partitionKey.filter(_.nonEmpty).map("partitionKey" -> _)
        ).flatten.toMap
      )
    }

  private def requireOpsAdmin(context: ApiPlanContext): AccessPrincipal =
    val operator = context.support.principal(operatorId)
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator

  private def paged(items: Vector[DomainEventSubscriberPartitionStatus], appliedFilters: Map[String, String]): PagedResponse[DomainEventSubscriberPartitionStatus] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters)

  private def subscriberPartitionStatuses(
      context: ApiPlanContext,
      subscriberId: String,
      asOf: Instant,
      lagOnly: Boolean,
      blockedOnly: Boolean,
      partitionKey: Option[String]
  ): Vector[DomainEventSubscriberPartitionStatus] =
    val subscriber = resolveSubscriber(context, subscriberId)
    buildPartitionStatuses(context, subscriber, asOf)
      .filter(status => partitionKey.forall(_ == status.partitionKey))
      .filter(status => !lagOnly || status.undeliveredCount > 0)
      .filter(status =>
        !blockedOnly || status.blockedByDeadLetter || status.blockedByQuarantine || status.blockedByRetryDelay ||
          status.blockedByInFlightProcessing || status.blockedBySequenceGap
      )
      .sortBy(status => (status.partitionKey, status.nextUndeliveredSequenceNo.getOrElse(Long.MaxValue)))

  private def resolveSubscriber(context: ApiPlanContext, subscriberId: String): DomainEventSubscriber =
    context.support.opsAnalyticsModule.domainEventSubscribers
      .find(_.subscriberId == subscriberId)
      .getOrElse(throw NoSuchElementException(s"Domain event subscriber $subscriberId was not registered"))

  private def buildPartitionStatuses(
      context: ApiPlanContext,
      subscriber: DomainEventSubscriber,
      asOf: Instant
  ): Vector[DomainEventSubscriberPartitionStatus] =
    val module = context.support.opsAnalyticsModule
    val records = module.domainEventOutboxRepository.findAll().sortBy(_.sequenceNo)
    val recordsById = records.map(record => record.id -> record).toMap
    val receipts = module.domainEventDeliveryReceiptRepository.findAll()
      .filter(_.subscriberId == subscriber.subscriberId)
    val receiptByRecordId = receipts.map(receipt => receipt.outboxRecordId -> receipt).toMap
    val cursors = module.domainEventSubscriberCursorRepository.findAll()
      .filter(_.subscriberId == subscriber.subscriberId)
    val cursorByPartition = cursors.map(cursor => cursor.partitionKey -> cursor).toMap
    val relevantPartitionsFromRecords = records.map(subscriber.partitionStrategy.partitionKey).distinct
    val relevantPartitions = (relevantPartitionsFromRecords ++ cursorByPartition.keys).distinct

    relevantPartitions.map { currentPartitionKey =>
      val partitionRecords = records.filter(record =>
        subscriber.partitionStrategy.partitionKey(record) == currentPartitionKey
      )
      val undeliveredRecords = partitionRecords.filterNot(record => receiptByRecordId.contains(record.id))
      val nextUndelivered = undeliveredRecords.headOption
      val lastDeliveredReceipt = partitionRecords.reverseIterator
        .map(record => receiptByRecordId.get(record.id))
        .collectFirst { case Some(receipt) => receipt }
      val cursor = cursorByPartition.get(currentPartitionKey)

      DomainEventSubscriberPartitionStatus(
        subscriberId = subscriber.subscriberId,
        partitionStrategy = subscriber.partitionStrategy.toString,
        partitionKey = currentPartitionKey,
        cursor = cursor,
        lastDeliveredAt = lastDeliveredReceipt.map(_.deliveredAt).orElse(cursor.map(_.advancedAt)),
        lastDeliveredSequenceNo =
          cursor.map(_.lastDeliveredSequenceNo)
            .orElse(lastDeliveredReceipt.flatMap(receipt => recordsById.get(receipt.outboxRecordId).map(_.sequenceNo))),
        undeliveredCount = undeliveredRecords.size,
        deadLetterUndeliveredCount = undeliveredRecords.count(_.status == DomainEventOutboxStatus.DeadLetter),
        quarantinedUndeliveredCount = undeliveredRecords.count(_.status == DomainEventOutboxStatus.Quarantined),
        readyUndeliveredCount = undeliveredRecords.count(record =>
          record.status == DomainEventOutboxStatus.Pending && !record.availableAt.isAfter(asOf)
        ),
        nextUndeliveredRecordId = nextUndelivered.map(_.id),
        nextUndeliveredSequenceNo = nextUndelivered.map(_.sequenceNo),
        nextUndeliveredEventType = nextUndelivered.map(_.eventType),
        nextUndeliveredStatus = nextUndelivered.map(_.status),
        nextUndeliveredOccurredAt = nextUndelivered.map(_.occurredAt),
        nextUndeliveredAvailableAt = nextUndelivered.map(_.availableAt),
        blockedByDeadLetter = nextUndelivered.exists(_.status == DomainEventOutboxStatus.DeadLetter),
        blockedByQuarantine = nextUndelivered.exists(_.status == DomainEventOutboxStatus.Quarantined),
        blockedByRetryDelay = nextUndelivered.exists(record =>
          record.status == DomainEventOutboxStatus.Pending && record.availableAt.isAfter(asOf)
        ),
        blockedByInFlightProcessing = nextUndelivered.exists(_.status == DomainEventOutboxStatus.Processing),
        blockedBySequenceGap = nextUndelivered.exists(record =>
          undeliveredRecords.exists(_.sequenceNo > record.sequenceNo)
        )
      )
    }
