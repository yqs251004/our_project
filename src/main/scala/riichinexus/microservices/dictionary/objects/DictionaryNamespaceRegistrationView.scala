package riichinexus.microservices.dictionary.objects

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class DictionaryNamespaceRegistrationView(
    namespacePrefix: String,
    status: String,
    requestedBy: String,
    requestedAt: String,
    reviewedBy: Option[String],
    reviewedAt: Option[String],
    ownerPlayerId: String,
    coOwnerPlayerIds: Vector[String],
    editorPlayerIds: Vector[String],
    contextClubId: Option[String],
    reviewDueAt: Option[String],
    note: Option[String]
) derives ReadWriter

object DictionaryNamespaceRegistrationView:
  def fromDomain(registration: DictionaryNamespaceRegistration): DictionaryNamespaceRegistrationView =
    DictionaryNamespaceRegistrationView(
      namespacePrefix = registration.namespacePrefix,
      status = registration.status.toString,
      requestedBy = registration.requestedBy.value,
      requestedAt = registration.requestedAt.toString,
      reviewedBy = registration.reviewedBy.map(_.value),
      reviewedAt = registration.reviewedAt.map(_.toString),
      ownerPlayerId = registration.ownerPlayerId.value,
      coOwnerPlayerIds = registration.coOwnerPlayerIds.map(_.value),
      editorPlayerIds = registration.editorPlayerIds.map(_.value),
      contextClubId = registration.contextClubId.map(_.value),
      reviewDueAt = registration.reviewDueAt.map(_.toString),
      note = registration.reviewNote
    )
