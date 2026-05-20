package riichinexus.microservices.opsanalytics.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListEventCascadeRecordsAPIMessage(
    operatorId: PlayerId,
    status: Option[EventCascadeStatus] = None,
    consumer: Option[EventCascadeConsumer] = None,
    eventType: Option[String] = None,
    aggregateType: Option[String] = None,
    aggregateId: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[EventCascadeRecord]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[EventCascadeRecord]] =
    IO {
      val operator = context.support.principal(operatorId)
      context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
      val parsedEventType = eventType.filter(_.nonEmpty)
      val parsedAggregateType = aggregateType.filter(_.nonEmpty)
      val parsedAggregateId = aggregateId.filter(_.nonEmpty)
      paged(
        context.support.opsAnalyticsModule.tables.listEventCascadeRecords()
          .filter(record => status.forall(_ == record.status))
          .filter(record => consumer.forall(_ == record.consumer))
          .filter(record => parsedEventType.forall(_ == record.eventType))
          .filter(record => parsedAggregateType.forall(_ == record.aggregateType))
          .filter(record => parsedAggregateId.forall(_ == record.aggregateId))
          .sortBy(record => (record.occurredAt, record.id.value)),
        Vector(
          status.map(value => "status" -> value.toString),
          consumer.map(value => "consumer" -> value.toString),
          eventType.filter(_.nonEmpty).map("eventType" -> _),
          aggregateType.filter(_.nonEmpty).map("aggregateType" -> _),
          aggregateId.filter(_.nonEmpty).map("aggregateId" -> _)
        ).flatten.toMap
      )
    }

  private def paged(items: Vector[EventCascadeRecord], appliedFilters: Map[String, String]): PagedResponse[EventCascadeRecord] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(page, items.size, boundedLimit, resolvedOffset, resolvedOffset + page.size < items.size, appliedFilters)
