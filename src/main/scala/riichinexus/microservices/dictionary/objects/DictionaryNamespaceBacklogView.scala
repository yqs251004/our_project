package riichinexus.microservices.dictionary.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class DictionaryNamespaceBacklogView(
    asOf: String,
    pendingCount: Int,
    overdueCount: Int,
    dueSoonCount: Int,
    oldestPendingRequestedAt: Option[String],
    nextDueAt: Option[String],
    ownerBacklog: Vector[DictionaryNamespaceOwnerBacklog]
) derives ReadWriter
