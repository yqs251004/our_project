package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.{
  DictionaryNamespaceRegistration,
  DictionaryNamespaceRegistrationView,
  DictionaryNamespaceReviewStatus
}
import riichinexus.microservices.dictionary.objects.apiTypes.{
  DictionaryListNamespacesQuery
}
import riichinexus.microservices.dictionary.tables.dictionarynamespace.DictionaryNamespaceTable
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class DictionaryListNamespacesAPIMessage(
    query: DictionaryListNamespacesQuery
) extends APIMessage[PagedResponse[DictionaryNamespaceRegistrationView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[DictionaryNamespaceRegistrationView]] =
    for
      now <- IO.realTimeInstant
      resolved <- IO(resolveQuery(context, now))
      namespaces <- IO(listNamespaces(context, resolved))
    yield PagedResponse.fromItems(namespaces, query.limit, query.offset, resolved.appliedFilters)(
      DictionaryNamespaceRegistrationView.fromDomain
    )

  private def resolveQuery(context: ApiPlanContext, now: Instant): ResolvedNamespacesQuery =
    ResolvedNamespacesQuery(
      status = query.status.filter(_.nonEmpty).map(value => context.support.parseEnum("status", value)(DictionaryNamespaceReviewStatus.valueOf)),
      contextClubId = query.contextClubId.filter(_.nonEmpty).map(ClubId(_)),
      ownerId = query.ownerId.filter(_.nonEmpty).map(PlayerId(_)),
      requestedBy = query.requestedBy.filter(_.nonEmpty).map(PlayerId(_)),
      reviewedBy = query.reviewedBy.filter(_.nonEmpty).map(PlayerId(_)),
      asOf = query.asOf.filter(_.nonEmpty).map(Instant.parse).getOrElse(now),
      overdueOnly = query.overdueOnly.contains(true),
      dueBefore = query.dueBefore.filter(_.nonEmpty).map(Instant.parse),
      dueAfter = query.dueAfter.filter(_.nonEmpty).map(Instant.parse),
      actor = context.principal(PlayerId(query.operatorId)),
      appliedFilters = Vector(
        query.status.filter(_.nonEmpty).map("status" -> _),
        query.contextClubId.filter(_.nonEmpty).map("contextClubId" -> _),
        query.ownerId.filter(_.nonEmpty).map("ownerId" -> _),
        query.requestedBy.filter(_.nonEmpty).map("requestedBy" -> _),
        query.reviewedBy.filter(_.nonEmpty).map("reviewedBy" -> _),
        query.asOf.filter(_.nonEmpty).map("asOf" -> _),
        query.overdueOnly.map(value => "overdueOnly" -> value.toString),
        query.dueBefore.filter(_.nonEmpty).map("dueBefore" -> _),
        query.dueAfter.filter(_.nonEmpty).map("dueAfter" -> _)
      ).flatten.toMap
    )

  private def listNamespaces(
      context: ApiPlanContext,
      query: ResolvedNamespacesQuery
  ): Vector[DictionaryNamespaceRegistration] =
    DictionaryNamespaceTable.findAll(context.connection)
      .filter(registration => query.status.forall(_ == registration.status))
      .filter(registration => query.contextClubId.forall(clubId => registration.contextClubId.contains(clubId)))
      .filter(registration => query.ownerId.forall(_ == registration.ownerPlayerId))
      .filter(registration => query.requestedBy.forall(_ == registration.requestedBy))
      .filter(registration => query.reviewedBy.forall(reviewer => registration.reviewedBy.contains(reviewer)))
      .filter(registration => !query.overdueOnly || registration.isPendingOverdue(query.asOf))
      .filter(registration => query.dueBefore.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isAfter(bound))))
      .filter(registration => query.dueAfter.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isBefore(bound))))
      .filter(registration =>
        query.actor.isSuperAdmin ||
          query.actor.playerId.exists(registration.hasWriteAccess) ||
          query.actor.playerId.contains(registration.requestedBy)
      )

  private final case class ResolvedNamespacesQuery(
      status: Option[DictionaryNamespaceReviewStatus],
      contextClubId: Option[ClubId],
      ownerId: Option[PlayerId],
      requestedBy: Option[PlayerId],
      reviewedBy: Option[PlayerId],
      asOf: Instant,
      overdueOnly: Boolean,
      dueBefore: Option[Instant],
      dueAfter: Option[Instant],
      actor: AccessPrincipal,
      appliedFilters: Map[String, String]
  )
