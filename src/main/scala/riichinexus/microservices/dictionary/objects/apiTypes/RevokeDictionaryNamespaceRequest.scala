package riichinexus.microservices.dictionary.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class RevokeDictionaryNamespaceRequest(
    operatorId: String,
    namespacePrefix: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object RevokeDictionaryNamespaceRequest:
  given ReadWriter[RevokeDictionaryNamespaceRequest] = macroRW
