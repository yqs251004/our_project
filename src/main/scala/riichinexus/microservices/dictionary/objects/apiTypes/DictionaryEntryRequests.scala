package riichinexus.microservices.dictionary.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class DictionaryListEntriesQuery(
    prefix: Option[String] = None,
    updatedBy: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object DictionaryListEntriesQuery:
  given ReadWriter[DictionaryListEntriesQuery] = macroRW

final case class UpsertDictionaryRequest(
    operatorId: String,
    key: String,
    value: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object UpsertDictionaryRequest:
  given ReadWriter[UpsertDictionaryRequest] = macroRW
