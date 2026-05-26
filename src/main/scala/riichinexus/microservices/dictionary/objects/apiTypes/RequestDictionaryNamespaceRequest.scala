package riichinexus.microservices.dictionary.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class RequestDictionaryNamespaceRequest(
    operatorId: String,
    namespacePrefix: String,
    contextClubId: Option[String] = None,
    ownerPlayerId: Option[String] = None,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    editorPlayerIds: Vector[String] = Vector.empty,
    note: Option[String] = None,
    reviewDueAt: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def owner: Option[PlayerId] =
    ownerPlayerId.map(PlayerId(_))

  def contextClub: Option[ClubId] =
    contextClubId.map(ClubId(_))

  def coOwners: Vector[PlayerId] =
    coOwnerPlayerIds.map(PlayerId(_)).distinct

  def editors: Vector[PlayerId] =
    editorPlayerIds.map(PlayerId(_)).distinct

  def parsedReviewDueAt: Option[Instant] =
    reviewDueAt.map(Instant.parse)

object RequestDictionaryNamespaceRequest:
  given ReadWriter[RequestDictionaryNamespaceRequest] = macroRW
