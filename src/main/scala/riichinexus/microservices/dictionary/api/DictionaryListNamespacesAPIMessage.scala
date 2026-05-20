package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class DictionaryListNamespacesAPIMessage(
    operatorId: String,
    status: Option[String] = None,
    contextClubId: Option[String] = None,
    ownerId: Option[String] = None,
    requestedBy: Option[String] = None,
    reviewedBy: Option[String] = None,
    asOf: Option[String] = None,
    overdueOnly: Option[Boolean] = None,
    dueBefore: Option[String] = None,
    dueAfter: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[DictionaryNamespaceRegistration]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[DictionaryNamespaceRegistration]] =
    IO {
      val parsedStatus = status.filter(_.nonEmpty).map(value => context.support.parseEnum("status", value)(DictionaryNamespaceReviewStatus.valueOf))
      val parsedContextClubId = contextClubId.filter(_.nonEmpty).map(ClubId(_))
      val parsedOwnerId = ownerId.filter(_.nonEmpty).map(PlayerId(_))
      val parsedRequestedBy = requestedBy.filter(_.nonEmpty).map(PlayerId(_))
      val parsedReviewedBy = reviewedBy.filter(_.nonEmpty).map(PlayerId(_))
      val parsedAsOf = asOf.filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
      val parsedOverdueOnly = overdueOnly.contains(true)
      val parsedDueBefore = dueBefore.filter(_.nonEmpty).map(Instant.parse)
      val parsedDueAfter = dueAfter.filter(_.nonEmpty).map(Instant.parse)
      val actor = context.support.principal(PlayerId(operatorId))
      val namespaces = context.support.dictionaryModule.tables.listNamespaces()
        .filter(registration => parsedStatus.forall(_ == registration.status))
        .filter(registration => parsedContextClubId.forall(clubId => registration.contextClubId.contains(clubId)))
        .filter(registration => parsedOwnerId.forall(_ == registration.ownerPlayerId))
        .filter(registration => parsedRequestedBy.forall(_ == registration.requestedBy))
        .filter(registration => parsedReviewedBy.forall(reviewer => registration.reviewedBy.contains(reviewer)))
        .filter(registration => !parsedOverdueOnly || registration.isPendingOverdue(parsedAsOf))
        .filter(registration => parsedDueBefore.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isAfter(bound))))
        .filter(registration => parsedDueAfter.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isBefore(bound))))
        .filter(registration =>
          actor.isSuperAdmin ||
            actor.playerId.exists(registration.hasWriteAccess) ||
            actor.playerId.contains(registration.requestedBy)
        )
      page(
        namespaces,
        filters(
          status.filter(_.nonEmpty).map("status" -> _),
          contextClubId.filter(_.nonEmpty).map("contextClubId" -> _),
          ownerId.filter(_.nonEmpty).map("ownerId" -> _),
          requestedBy.filter(_.nonEmpty).map("requestedBy" -> _),
          reviewedBy.filter(_.nonEmpty).map("reviewedBy" -> _),
          asOf.filter(_.nonEmpty).map("asOf" -> _),
          overdueOnly.map(value => "overdueOnly" -> value.toString),
          dueBefore.filter(_.nonEmpty).map("dueBefore" -> _),
          dueAfter.filter(_.nonEmpty).map("dueAfter" -> _)
        )
      )
    }

  private def page(items: Vector[DictionaryNamespaceRegistration], appliedFilters: Map[String, String]): PagedResponse[DictionaryNamespaceRegistration] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
