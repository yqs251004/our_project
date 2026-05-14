package riichinexus.microservices.dictionary.api.requests

import riichinexus.domain.model.PlayerId
import upickle.default.*

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
