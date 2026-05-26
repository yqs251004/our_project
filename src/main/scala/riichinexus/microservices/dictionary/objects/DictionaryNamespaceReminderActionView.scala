package riichinexus.microservices.dictionary.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class DictionaryNamespaceReminderActionView(
    namespacePrefix: String,
    action: String,
    ownerPlayerId: String,
    occurredAt: String,
    note: Option[String]
) derives ReadWriter

object DictionaryNamespaceReminderActionView:
  def fromDomain(action: DictionaryNamespaceReminderAction): DictionaryNamespaceReminderActionView =
    DictionaryNamespaceReminderActionView(
      namespacePrefix = action.namespacePrefix,
      action = action.reminderKind.toString,
      ownerPlayerId = action.ownerPlayerId.value,
      occurredAt = action.triggeredAt.toString,
      note = action.dueAt.map(dueAt => s"dueAt=$dueAt; reminderCount=${action.reminderCount}")
    )
