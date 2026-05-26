package riichinexus.microservices.dictionary.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class GlobalDictionaryEntryView(
    key: String,
    value: String,
    updatedBy: Option[String],
    updatedAt: String,
    note: Option[String]
) derives ReadWriter

object GlobalDictionaryEntryView:
  def fromDomain(entry: GlobalDictionaryEntry): GlobalDictionaryEntryView =
    GlobalDictionaryEntryView(
      key = entry.key,
      value = entry.value,
      updatedBy = Some(entry.updatedBy.value),
      updatedAt = entry.updatedAt.toString,
      note = entry.note
    )
