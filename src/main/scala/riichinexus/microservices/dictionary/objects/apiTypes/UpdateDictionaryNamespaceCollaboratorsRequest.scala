package riichinexus.microservices.dictionary.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpdateDictionaryNamespaceCollaboratorsRequest(
    operatorId: String,
    namespacePrefix: String,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    editorPlayerIds: Vector[String] = Vector.empty,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def coOwners: Vector[PlayerId] =
    coOwnerPlayerIds.map(PlayerId(_)).distinct

  def editors: Vector[PlayerId] =
    editorPlayerIds.map(PlayerId(_)).distinct

object UpdateDictionaryNamespaceCollaboratorsRequest:
  given ReadWriter[UpdateDictionaryNamespaceCollaboratorsRequest] = macroRW
