package riichinexus.microservices.dictionary.objects

import java.time.Instant

final case class GlobalDictionaryEntry(
    key: String,
    value: String,
    updatedAt: Instant,
    updatedBy: riichinexus.domain.model.PlayerId,
    note: Option[String] = None,
    version: Int = 0
) derives CanEqual
