package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.time.{Duration, Instant}

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryProcessNamespaceRemindersAPIMessage(
    operatorId: String,
    asOf: Option[String] = None,
    dueSoonHours: Int = 24,
    reminderIntervalHours: Int = 12,
    escalationGraceHours: Int = 72
) extends APIMessage[Vector[DictionaryNamespaceReminderAction]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[DictionaryNamespaceReminderAction]] =
    IO {
      val module = context.support.dictionaryModule
      val request = ProcessDictionaryNamespaceRemindersRequest(operatorId, asOf, dueSoonHours, reminderIntervalHours, escalationGraceHours)
      val actor = context.support.principal(request.operator)
      val resolvedAsOf = request.parsedAsOf.getOrElse(Instant.now())
      val dueSoonWindow = Duration.ofHours(request.dueSoonHours.toLong)
      val reminderInterval = Duration.ofHours(request.reminderIntervalHours.toLong)
      val escalationGrace = Duration.ofHours(request.escalationGraceHours.toLong)

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
        module.tables.listNamespaces()
          .filter(_.status == DictionaryNamespaceReviewStatus.Pending)
          .flatMap { registration =>
            reminderKindFor(registration, resolvedAsOf, escalationGrace)
              .filter { kind =>
                kind != DictionaryNamespaceReminderKind.DueSoon || registration.isPendingDueSoon(resolvedAsOf, dueSoonWindow)
              }
              .filter(_ =>
                registration.lastReminderAt.forall(lastSentAt =>
                  lastSentAt.plus(reminderInterval).isBefore(resolvedAsOf) || lastSentAt.plus(reminderInterval).equals(resolvedAsOf)
                )
              )
              .map { reminderKind =>
                val updated = registration.markReminderSent(resolvedAsOf)
                module.dictionaryNamespaceRepository.save(updated)
                module.auditEventRepository.save(
                  AuditEventEntry(
                    id = IdGenerator.auditEventId(),
                    aggregateType = "dictionary-namespace",
                    aggregateId = registration.namespacePrefix,
                    eventType = "DictionaryNamespaceReminderTriggered",
                    occurredAt = resolvedAsOf,
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
                  triggeredAt = resolvedAsOf,
                  dueAt = registration.reviewDueAt,
                  reminderCount = updated.reminderCount
                )
              }
          }
          .sortBy(action => (action.namespacePrefix, action.reminderKind.toString))
      }
    }

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
