package riichinexus.microservices.dictionary.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class GlobalDictionarySchemaView(
    entries: Vector[GlobalDictionarySchemaEntryView],
    unknownKeyPolicy: String
) derives CanEqual

object GlobalDictionarySchemaView:
  given ReadWriter[GlobalDictionarySchemaView] = macroRW
