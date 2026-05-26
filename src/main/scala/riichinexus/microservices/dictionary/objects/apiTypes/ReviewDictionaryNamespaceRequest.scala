package riichinexus.microservices.dictionary.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ReviewDictionaryNamespaceRequest(
    operatorId: String,
    namespacePrefix: String,
    approve: Boolean,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object ReviewDictionaryNamespaceRequest:
  given ReadWriter[ReviewDictionaryNamespaceRequest] = macroRW
