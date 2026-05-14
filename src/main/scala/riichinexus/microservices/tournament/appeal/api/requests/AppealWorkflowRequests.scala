package riichinexus.microservices.tournament.appeal.api.requests

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AppealAttachmentRequest(
    name: String,
    uri: String,
    contentType: Option[String] = None,
    storageKind: Option[String] = None,
    mediaKind: Option[String] = None,
    checksum: Option[String] = None,
    checksumAlgorithm: Option[String] = None,
    sizeBytes: Option[Long] = None,
    uploadedAt: Option[Instant] = None,
    retentionUntil: Option[Instant] = None
):
  def toAttachment: AppealAttachment =
    AppealAttachment(
      name = name,
      uri = uri,
      contentType = contentType,
      storageKind = storageKind.map(AppealAttachmentStorageKind.valueOf).getOrElse(AppealAttachmentStorageKind.ExternalUrl),
      mediaKind = mediaKind.map(AppealAttachmentMediaKind.valueOf).getOrElse(AppealAttachmentMediaKind.Other),
      checksum = checksum,
      checksumAlgorithm = checksumAlgorithm,
      sizeBytes = sizeBytes,
      uploadedAt = uploadedAt,
      retentionUntil = retentionUntil
    )

object AppealAttachmentRequest:
  given ReadWriter[AppealAttachmentRequest] = macroRW

final case class FileAppealRequest(
    playerId: String,
    description: String,
    attachments: Vector[AppealAttachmentRequest] = Vector.empty,
    priority: Option[String] = None,
    dueAt: Option[String] = None
):
  def player: PlayerId =
    PlayerId(playerId)

  def priorityLevel: AppealPriority =
    priority.map(AppealPriority.valueOf).getOrElse(AppealPriority.Normal)

  def dueAtInstant: Option[Instant] =
    dueAt.map(Instant.parse)

object FileAppealRequest:
  given ReadWriter[FileAppealRequest] = macroRW

final case class ResolveAppealRequest(
    operatorId: String,
    verdict: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object ResolveAppealRequest:
  given ReadWriter[ResolveAppealRequest] = macroRW

final case class AdjudicateAppealRequest(
    operatorId: String,
    decision: String,
    verdict: String,
    tableResolution: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def decisionType: AppealDecisionType =
    AppealDecisionType.valueOf(decision)

  def resolution: Option[AppealTableResolution] =
    tableResolution.map(AppealTableResolution.valueOf)

object AdjudicateAppealRequest:
  given ReadWriter[AdjudicateAppealRequest] = macroRW

final case class UpdateAppealWorkflowRequest(
    operatorId: String,
    assigneeId: Option[String] = None,
    clearAssignee: Boolean = false,
    priority: Option[String] = None,
    dueAt: Option[String] = None,
    clearDueAt: Boolean = false,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def assignee: Option[PlayerId] =
    assigneeId.map(PlayerId(_))

  def priorityLevel: Option[AppealPriority] =
    priority.map(AppealPriority.valueOf)

  def dueAtInstant: Option[Instant] =
    dueAt.map(Instant.parse)

object UpdateAppealWorkflowRequest:
  given ReadWriter[UpdateAppealWorkflowRequest] = macroRW

final case class ReopenAppealRequest(
    operatorId: String,
    reason: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object ReopenAppealRequest:
  given ReadWriter[ReopenAppealRequest] = macroRW
