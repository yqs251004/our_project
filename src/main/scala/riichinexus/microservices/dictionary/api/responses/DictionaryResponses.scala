package riichinexus.microservices.dictionary.api.responses

import java.time.Instant

import riichinexus.domain.model.*

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

  export DictionaryResponseCodecs.given
