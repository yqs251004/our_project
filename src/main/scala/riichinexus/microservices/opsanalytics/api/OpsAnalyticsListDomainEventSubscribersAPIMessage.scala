package riichinexus.microservices.opsanalytics.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.{DomainEventSubscriberStatus as DomainEventSubscriberStatusResponse}
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventResponses.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.DomainEventSubscribersQuery
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsDomainEventSubscriberQueries
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class OpsAnalyticsListDomainEventSubscribersAPIMessage(
    query: DomainEventSubscribersQuery
) extends APIMessage[PagedResponse[DomainEventSubscriberStatusResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[DomainEventSubscriberStatusResponse]] =
    for
      _ <- IO(requireOpsAdmin(context))
      asOf <- IO.realTimeInstant
      resolved <- IO(resolveQuery(asOf))
      subscribers <- IO(listSubscribers(context, resolved))
    yield PagedResponse.fromItems(subscribers, query.limit, query.offset, resolved.appliedFilters)(
      DomainEventSubscriberStatusResponse.fromDomain
    )

  private def resolveQuery(defaultAsOf: Instant): ResolvedSubscribersQuery =
    ResolvedSubscribersQuery(
      asOf = query.asOf.getOrElse(defaultAsOf),
      subscriberId = query.subscriberId.filter(_.nonEmpty),
      appliedFilters = Vector(
        query.asOf.map(value => "asOf" -> value.toString),
        query.subscriberId.filter(_.nonEmpty).map("subscriberId" -> _)
      ).flatten.toMap
    )

  private def listSubscribers(
      context: ApiPlanContext,
      resolved: ResolvedSubscribersQuery
  ): Vector[DomainEventSubscriberStatus] =
    OpsAnalyticsDomainEventSubscriberQueries.subscriberStatuses(
      context.support.opsAnalyticsModule,
      resolved.asOf,
      resolved.subscriberId
    )

  private def requireOpsAdmin(context: ApiPlanContext): AccessPrincipal =
    val operator = context.support.principal(query.operatorId)
    context.support.requirePermission(operator, Permission.ManageGlobalDictionary)
    operator

  private final case class ResolvedSubscribersQuery(
      asOf: Instant,
      subscriberId: Option[String],
      appliedFilters: Map[String, String]
  )
