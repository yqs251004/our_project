package riichinexus.microservices.dictionary.api.requests

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId}
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

final case class ProcessDictionaryNamespaceRemindersRequest(
    operatorId: String,
    asOf: Option[String] = None,
    dueSoonHours: Int = 24,
    reminderIntervalHours: Int = 12,
    escalationGraceHours: Int = 72
):
  require(dueSoonHours > 0, "Dictionary namespace dueSoonHours must be positive")
  require(reminderIntervalHours > 0, "Dictionary namespace reminderIntervalHours must be positive")
  require(escalationGraceHours > 0, "Dictionary namespace escalationGraceHours must be positive")

  def operator: PlayerId =
    PlayerId(operatorId)

  def parsedAsOf: Option[Instant] =
    asOf.map(Instant.parse)

object ProcessDictionaryNamespaceRemindersRequest:
  given ReadWriter[ProcessDictionaryNamespaceRemindersRequest] = macroRW

final case class RevokeDictionaryNamespaceRequest(
    operatorId: String,
    namespacePrefix: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object RevokeDictionaryNamespaceRequest:
  given ReadWriter[RevokeDictionaryNamespaceRequest] = macroRW
