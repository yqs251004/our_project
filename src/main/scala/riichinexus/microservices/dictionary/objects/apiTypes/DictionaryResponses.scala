package riichinexus.microservices.dictionary.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{
  DictionaryNamespaceRegistration as DomainDictionaryNamespaceRegistration,
  DictionaryNamespaceReminderAction as DomainDictionaryNamespaceReminderAction,
  GlobalDictionaryEntry as DomainGlobalDictionaryEntry,
  GlobalDictionarySchemaEntry as DomainGlobalDictionarySchemaEntry,
  PlayerId
}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class GlobalDictionarySchemaEntry(
    key: String,
    description: String,
    valueType: String,
    defaultValue: String
) derives ReadWriter

object GlobalDictionarySchemaEntry:
  def fromDomain(entry: DomainGlobalDictionarySchemaEntry): GlobalDictionarySchemaEntry =
    GlobalDictionarySchemaEntry(
      key = entry.keyPattern,
      description = entry.description,
      valueType = entry.valueType.toString,
      defaultValue = entry.examples.headOption.getOrElse("")
    )

final case class GlobalDictionaryEntry(
    key: String,
    value: String,
    updatedBy: Option[String],
    updatedAt: String,
    note: Option[String]
) derives ReadWriter

object GlobalDictionaryEntry:
  def fromDomain(entry: DomainGlobalDictionaryEntry): GlobalDictionaryEntry =
    GlobalDictionaryEntry(
      key = entry.key,
      value = entry.value,
      updatedBy = Some(entry.updatedBy.value),
      updatedAt = entry.updatedAt.toString,
      note = entry.note
    )

final case class DictionaryNamespaceRegistration(
    namespacePrefix: String,
    status: String,
    requestedBy: String,
    requestedAt: String,
    reviewedBy: Option[String],
    reviewedAt: Option[String],
    ownerPlayerId: String,
    coOwnerPlayerIds: Vector[String],
    editorPlayerIds: Vector[String],
    contextClubId: Option[String],
    reviewDueAt: Option[String],
    note: Option[String]
) derives ReadWriter

object DictionaryNamespaceRegistration:
  def fromDomain(registration: DomainDictionaryNamespaceRegistration): DictionaryNamespaceRegistration =
    DictionaryNamespaceRegistration(
      namespacePrefix = registration.namespacePrefix,
      status = registration.status.toString,
      requestedBy = registration.requestedBy.value,
      requestedAt = registration.requestedAt.toString,
      reviewedBy = registration.reviewedBy.map(_.value),
      reviewedAt = registration.reviewedAt.map(_.toString),
      ownerPlayerId = registration.ownerPlayerId.value,
      coOwnerPlayerIds = registration.coOwnerPlayerIds.map(_.value),
      editorPlayerIds = registration.editorPlayerIds.map(_.value),
      contextClubId = registration.contextClubId.map(_.value),
      reviewDueAt = registration.reviewDueAt.map(_.toString),
      note = registration.reviewNote
    )

final case class DictionaryNamespaceReminderAction(
    namespacePrefix: String,
    action: String,
    ownerPlayerId: String,
    occurredAt: String,
    note: Option[String]
) derives ReadWriter

object DictionaryNamespaceReminderAction:
  def fromDomain(action: DomainDictionaryNamespaceReminderAction): DictionaryNamespaceReminderAction =
    DictionaryNamespaceReminderAction(
      namespacePrefix = action.namespacePrefix,
      action = action.reminderKind.toString,
      ownerPlayerId = action.ownerPlayerId.value,
      occurredAt = action.triggeredAt.toString,
      note = action.dueAt.map(dueAt => s"dueAt=$dueAt; reminderCount=${action.reminderCount}")
    )

final case class GlobalDictionarySchemaView(
    entries: Vector[GlobalDictionarySchemaEntry],
    unknownKeyPolicy: String
) derives CanEqual

final case class DictionaryNamespaceOwnerBacklog(
    ownerPlayerId: String,
    pendingCount: Int,
    overdueCount: Int,
    dueSoonCount: Int
) derives ReadWriter

final case class DictionaryNamespaceBacklogView(
    asOf: String,
    pendingCount: Int,
    overdueCount: Int,
    dueSoonCount: Int,
    oldestPendingRequestedAt: Option[String],
    nextDueAt: Option[String],
    ownerBacklog: Vector[DictionaryNamespaceOwnerBacklog]
) derives ReadWriter

object DictionaryResponses:
  type GlobalDictionarySchemaResponse = GlobalDictionarySchemaView
  type DictionaryNamespaceBacklogResponse = DictionaryNamespaceBacklogView

  given ReadWriter[GlobalDictionarySchemaView] = macroRW
