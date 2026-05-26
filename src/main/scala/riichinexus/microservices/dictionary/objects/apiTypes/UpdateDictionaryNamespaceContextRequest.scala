package riichinexus.microservices.dictionary.objects.apiTypes

import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class UpdateDictionaryNamespaceContextRequest(
    operatorId: String,
    namespacePrefix: String,
    contextClubId: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def contextClub: Option[ClubId] =
    contextClubId.map(ClubId(_))

object UpdateDictionaryNamespaceContextRequest:
  given ReadWriter[UpdateDictionaryNamespaceContextRequest] = macroRW
