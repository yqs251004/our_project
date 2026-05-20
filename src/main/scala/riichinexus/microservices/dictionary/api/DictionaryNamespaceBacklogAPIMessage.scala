package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.time.{Duration, Instant}

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import upickle.default.*

final case class DictionaryNamespaceBacklogAPIMessage(
    operatorId: String,
    asOf: Option[String] = None,
    dueSoonHours: Option[Long] = None
) extends APIMessage[DictionaryNamespaceBacklogView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceBacklogView] =
    IO {
      val module = context.support.dictionaryModule
      val actor = context.support.principal(PlayerId(operatorId))
      val resolvedAsOf = asOf.filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
      val dueSoonWindow = Duration.ofHours(dueSoonHours.getOrElse(24L))

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
        val pending = module.tables.listNamespaces()
          .filter(_.status == DictionaryNamespaceReviewStatus.Pending)

        val ownerBacklog = pending
          .groupBy(_.ownerPlayerId)
          .toVector
          .map { case (ownerId, registrations) =>
            DictionaryNamespaceOwnerBacklog(
              ownerPlayerId = ownerId,
              pendingCount = registrations.size,
              overdueCount = registrations.count(_.isPendingOverdue(resolvedAsOf)),
              dueSoonCount = registrations.count(_.isPendingDueSoon(resolvedAsOf, dueSoonWindow))
            )
          }
          .sortBy(bucket => (-bucket.overdueCount, -bucket.pendingCount, bucket.ownerPlayerId.value))

        DictionaryNamespaceBacklogView(
          asOf = resolvedAsOf,
          pendingCount = pending.size,
          overdueCount = pending.count(_.isPendingOverdue(resolvedAsOf)),
          dueSoonCount = pending.count(_.isPendingDueSoon(resolvedAsOf, dueSoonWindow)),
          oldestPendingRequestedAt = pending.map(_.requestedAt).sorted.headOption,
          nextDueAt = pending.flatMap(_.reviewDueAt).sorted.headOption,
          ownerBacklog = ownerBacklog
        )
      }
    }
