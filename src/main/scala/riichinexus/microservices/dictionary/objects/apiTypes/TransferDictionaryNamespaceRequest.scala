package riichinexus.microservices.dictionary.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TransferDictionaryNamespaceRequest(
    operatorId: String,
    namespacePrefix: String,
    newOwnerPlayerId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def newOwner: PlayerId =
    PlayerId(newOwnerPlayerId)

object TransferDictionaryNamespaceRequest:
  given ReadWriter[TransferDictionaryNamespaceRequest] = macroRW
