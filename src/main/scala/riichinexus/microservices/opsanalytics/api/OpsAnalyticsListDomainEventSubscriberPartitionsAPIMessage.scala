package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{DomainEventSubscriberPartitionStatus as DomainEventSubscriberPartitionStatusResponse}
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventSubscriberPartitionsQuery
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsDomainEventSubscriberQueries
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListDomainEventSubscriberPartitionsAPIMessage(
    query: DomainEventSubscriberPartitionsQuery
) extends APIMessage[PagedResponse[DomainEventSubscriberPartitionStatusResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[DomainEventSubscriberPartitionStatusResponse]] =
    for
      _ <- IO(requireOpsAdmin(context))
      asOf <- IO.realTimeInstant
      resolved <- IO(resolveQuery(asOf))
      partitions <- IO(listPartitions(context, resolved))
    yield PagedResponse.fromItems(partitions, query.limit, query.offset, resolved.appliedFilters)(
      DomainEventSubscriberPartitionStatusResponse.fromDomain
    )

  private def resolveQuery(defaultAsOf: Instant): ResolvedSubscriberPartitionsQuery =
    ResolvedSubscriberPartitionsQuery(
      asOf = query.asOf.getOrElse(defaultAsOf),
      lagOnly = query.lagOnly.getOrElse(false),
      blockedOnly = query.blockedOnly.getOrElse(false),
      partitionKey = query.partitionKey.filter(_.nonEmpty),
      appliedFilters = Vector(
        query.asOf.map(value => "asOf" -> value.toString),
        query.lagOnly.map(value => "lagOnly" -> value.toString),
        query.blockedOnly.map(value => "blockedOnly" -> value.toString),
        query.partitionKey.filter(_.nonEmpty).map("partitionKey" -> _)
      ).flatten.toMap
    )

  private def listPartitions(
      context: ApiPlanContext,
      resolved: ResolvedSubscriberPartitionsQuery
  ): Vector[DomainEventSubscriberPartitionStatus] =
    OpsAnalyticsDomainEventSubscriberQueries.subscriberPartitionStatuses(
      module = context.support.opsAnalyticsModule,
      subscriberId = query.subscriberId,
      asOf = resolved.asOf,
      lagOnly = resolved.lagOnly,
      blockedOnly = resolved.blockedOnly,
      partitionKey = resolved.partitionKey
    )

  private def requireOpsAdmin(context: ApiPlanContext): AccessPrincipal =
    val operator = context.support.principal(query.operatorId)
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator

  private final case class ResolvedSubscriberPartitionsQuery(
      asOf: Instant,
      lagOnly: Boolean,
      blockedOnly: Boolean,
      partitionKey: Option[String],
      appliedFilters: Map[String, String]
  )
