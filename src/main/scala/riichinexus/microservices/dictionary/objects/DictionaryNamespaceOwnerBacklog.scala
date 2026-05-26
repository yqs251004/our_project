package riichinexus.microservices.dictionary.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class DictionaryNamespaceOwnerBacklog(
    ownerPlayerId: String,
    pendingCount: Int,
    overdueCount: Int,
    dueSoonCount: Int
) derives ReadWriter
