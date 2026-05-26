package riichinexus.microservices.dictionary.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class GlobalDictionarySchemaEntryView(
    key: String,
    description: String,
    valueType: String,
    defaultValue: String
) derives ReadWriter

object GlobalDictionarySchemaEntryView:
  def fromDomain(entry: GlobalDictionarySchemaEntry): GlobalDictionarySchemaEntryView =
    GlobalDictionarySchemaEntryView(
      key = entry.keyPattern,
      description = entry.description,
      valueType = entry.valueType.toString,
      defaultValue = entry.examples.headOption.getOrElse("")
    )
