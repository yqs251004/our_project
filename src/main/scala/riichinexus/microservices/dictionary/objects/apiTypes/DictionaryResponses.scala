package riichinexus.microservices.dictionary.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

type GlobalDictionarySchemaEntry = riichinexus.domain.model.GlobalDictionarySchemaEntry
type GlobalDictionaryEntry = riichinexus.domain.model.GlobalDictionaryEntry
type DictionaryNamespaceRegistration = riichinexus.domain.model.DictionaryNamespaceRegistration
type DictionaryNamespaceReminderAction = riichinexus.domain.model.DictionaryNamespaceReminderAction

final case class GlobalDictionarySchemaView(
    entries: Vector[GlobalDictionarySchemaEntry],
    unknownKeyPolicy: String
) derives CanEqual

final case class DictionaryNamespaceOwnerBacklog(
    ownerPlayerId: PlayerId,
    pendingCount: Int,
    overdueCount: Int,
    dueSoonCount: Int
) derives CanEqual

final case class DictionaryNamespaceBacklogView(
    asOf: Instant,
    pendingCount: Int,
    overdueCount: Int,
    dueSoonCount: Int,
    oldestPendingRequestedAt: Option[Instant],
    nextDueAt: Option[Instant],
    ownerBacklog: Vector[DictionaryNamespaceOwnerBacklog]
) derives CanEqual

object DictionaryResponses:
  type GlobalDictionarySchemaResponse = GlobalDictionarySchemaView
  type DictionaryNamespaceBacklogResponse = DictionaryNamespaceBacklogView

  given ReadWriter[GlobalDictionarySchemaView] = macroRW
  given ReadWriter[DictionaryNamespaceOwnerBacklog] = macroRW
  given ReadWriter[DictionaryNamespaceBacklogView] = macroRW
