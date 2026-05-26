package riichinexus.microservices.dictionary.domain

import java.sql.Connection
import java.time.{Duration, Instant}

import riichinexus.bootstrap.DictionaryModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.dictionary.objects.{
  DictionaryNamespaceRegistration,
  DictionaryNamespaceReminderAction,
  DictionaryNamespaceReminderKind,
  DictionaryNamespaceReviewStatus
}
import riichinexus.microservices.dictionary.tables.dictionarynamespace.DictionaryNamespaceTable

object DictionaryNamespaceReminderOperations:
  def processReminders(
      connection: Connection,
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      asOf: Instant,
      dueSoonWindow: Duration,
      reminderInterval: Duration,
      escalationGrace: Duration
  ): Vector[DictionaryNamespaceReminderAction] =
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      DictionaryNamespaceTable.findAll(connection)
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
            .map(reminderAction(module, actor, registration, _, asOf))
        }
        .sortBy(action => (action.namespacePrefix, action.reminderKind.toString))
    }

  private def reminderAction(
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      registration: DictionaryNamespaceRegistration,
      reminderKind: DictionaryNamespaceReminderKind,
      asOf: Instant
  ): DictionaryNamespaceReminderAction =
    val saved = DictionaryNamespaceAudit.commit(
      module = module,
      aggregate = registration.markReminderSent(asOf),
      eventType = "DictionaryNamespaceReminderTriggered",
      occurredAt = asOf,
      actorId = actor.playerId,
      details = DictionaryNamespaceAudit.details(registration) ++ Map(
        "reminderKind" -> reminderKind.toString,
        "reviewDueAt" -> registration.reviewDueAt.map(_.toString).getOrElse("")
      ),
      note = Some(s"Namespace ${registration.namespacePrefix} is ${reminderKind.toString.toLowerCase}")
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
      reminderCount = saved.reminderCount
    )

  private def reminderKindFor(
      registration: DictionaryNamespaceRegistration,
      asOf: Instant,
      escalationGrace: Duration
  ): Option[DictionaryNamespaceReminderKind] =
    registration.reviewDueAt.flatMap { dueAt =>
      if registration.status != DictionaryNamespaceReviewStatus.Pending then None
      else if !dueAt.isBefore(asOf) then Some(DictionaryNamespaceReminderKind.DueSoon)
      else if !dueAt.plus(escalationGrace).isAfter(asOf) then Some(DictionaryNamespaceReminderKind.Escalated)
      else Some(DictionaryNamespaceReminderKind.Overdue)
    }
