package riichinexus.microservices.dictionary.objects.apiTypes

import upickle.default.*

final case class DictionaryListEntriesQuery(
    prefix: Option[String] = None,
    updatedBy: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object DictionaryListEntriesQuery:
  given ReadWriter[DictionaryListEntriesQuery] = macroRW
