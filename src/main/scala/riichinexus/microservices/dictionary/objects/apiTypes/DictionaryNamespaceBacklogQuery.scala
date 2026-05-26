package riichinexus.microservices.dictionary.objects.apiTypes

import upickle.default.*

final case class DictionaryNamespaceBacklogQuery(
    operatorId: String,
    asOf: Option[String] = None,
    dueSoonHours: Option[Int] = None
)

object DictionaryNamespaceBacklogQuery:
  given ReadWriter[DictionaryNamespaceBacklogQuery] = macroRW
