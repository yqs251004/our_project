package riichinexus.microservices.dictionary.api

import java.time.Instant

import riichinexus.application.ports.{AuditEventRepository, DictionaryNamespaceRepository, TransactionManager}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.dictionary.api.responses.*
import riichinexus.microservices.dictionary.tables.DictionaryTables

private[api] final class DictionaryNamespaceReminderService(
    tables: DictionaryTables,
    dictionaryNamespaceRepository: DictionaryNamespaceRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService
):
  private def reminderKindFor(
      registration: DictionaryNamespaceRegistration,
      asOf: Instant,
      escalationGrace: java.time.Duration
  ): Option[DictionaryNamespaceReminderKind] =
    registration.reviewDueAt.flatMap { dueAt =>
      if registration.status != DictionaryNamespaceReviewStatus.Pending then None
      else if !dueAt.isBefore(asOf) then Some(DictionaryNamespaceReminderKind.DueSoon)
      else if !dueAt.plus(escalationGrace).isAfter(asOf) then Some(DictionaryNamespaceReminderKind.Escalated)
      else Some(DictionaryNamespaceReminderKind.Overdue)
    }

  def dictionaryNamespaceBacklog(
      actor: AccessPrincipal,
      asOf: Instant = Instant.now(),
      dueSoonWindow: java.time.Duration = java.time.Duration.ofHours(24)
  ): DictionaryNamespaceBacklogView =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val pending = tables.listNamespaces()
        .filter(_.status == DictionaryNamespaceReviewStatus.Pending)

      val ownerBacklog = pending
        .groupBy(_.ownerPlayerId)
        .toVector
        .map { case (ownerId, registrations) =>
          DictionaryNamespaceOwnerBacklog(
            ownerPlayerId = ownerId,
            pendingCount = registrations.size,
            overdueCount = registrations.count(_.isPendingOverdue(asOf)),
            dueSoonCount = registrations.count(_.isPendingDueSoon(asOf, dueSoonWindow))
          )
        }
        .sortBy(bucket => (-bucket.overdueCount, -bucket.pendingCount, bucket.ownerPlayerId.value))

      DictionaryNamespaceBacklogView(
        asOf = asOf,
        pendingCount = pending.size,
        overdueCount = pending.count(_.isPendingOverdue(asOf)),
        dueSoonCount = pending.count(_.isPendingDueSoon(asOf, dueSoonWindow)),
        oldestPendingRequestedAt = pending.map(_.requestedAt).sorted.headOption,
        nextDueAt = pending.flatMap(_.reviewDueAt).sorted.headOption,
        ownerBacklog = ownerBacklog
      )
    }

  def processDictionaryNamespaceReminders(
      actor: AccessPrincipal,
      asOf: Instant = Instant.now(),
      dueSoonWindow: java.time.Duration = java.time.Duration.ofHours(24),
      reminderInterval: java.time.Duration = java.time.Duration.ofHours(12),
      escalationGrace: java.time.Duration = java.time.Duration.ofHours(72)
  ): Vector[DictionaryNamespaceReminderAction] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      tables.listNamespaces()
        .filter(_.status == DictionaryNamespaceReviewStatus.Pending)
        .flatMap { registration =>
          reminderKindFor(registration, asOf, escalationGrace)
            .filter { kind =>
              kind != DictionaryNamespaceReminderKind.DueSoon || registration.isPendingDueSoon(asOf, dueSoonWindow)
            }
            .filter(_ =>
              registration.lastReminderAt.forall(lastSentAt =>
                lastSentAt.plus(reminderInterval).isBefore(asOf) || lastSentAt.plus(reminderInterval).equals(asOf)
              )
            )
            .map { reminderKind =>
              val updated = registration.markReminderSent(asOf)
              dictionaryNamespaceRepository.save(updated)
              auditEventRepository.save(
                AuditEventEntry(
                  id = IdGenerator.auditEventId(),
                  aggregateType = "dictionary-namespace",
                  aggregateId = registration.namespacePrefix,
                  eventType = "DictionaryNamespaceReminderTriggered",
                  occurredAt = asOf,
                  actorId = actor.playerId,
                  details = Map(
                    "contextClubId" -> registration.contextClubId.map(_.value).getOrElse(""),
                    "ownerPlayerId" -> registration.ownerPlayerId.value,
                    "coOwnerPlayerIds" -> registration.coOwnerPlayerIds.map(_.value).mkString(","),
                    "editorPlayerIds" -> registration.editorPlayerIds.map(_.value).mkString(","),
                    "reminderKind" -> reminderKind.toString,
                    "reminderCount" -> updated.reminderCount.toString,
                    "reviewDueAt" -> registration.reviewDueAt.map(_.toString).getOrElse("")
                  ),
                  note = Some(s"Namespace ${registration.namespacePrefix} is ${reminderKind.toString.toLowerCase}")
                )
              )
              DictionaryNamespaceReminderAction(
                namespacePrefix = registration.namespacePrefix,
                contextClubId = registration.contextClubId,
                ownerPlayerId = registration.ownerPlayerId,
                coOwnerPlayerIds = registration.coOwnerPlayerIds,
                editorPlayerIds = registration.editorPlayerIds,
                reminderKind = reminderKind,
                triggeredAt = asOf,
                dueAt = registration.reviewDueAt,
                reminderCount = updated.reminderCount
              )
            }
        }
        .sortBy(action => (action.namespacePrefix, action.reminderKind.toString))
    }
