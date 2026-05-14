package riichinexus.microservices.opsanalytics.api.requests

import java.time.Instant

import riichinexus.domain.model.{DomainEventOutboxRecordId, PlayerId}
import upickle.default.*

private given ReadWriter[Instant] =
  readwriter[String].bimap[Instant](_.toString, Instant.parse)

final case class ReplayDomainEventOutboxRequest(
    operatorId: String,
    replayAt: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def replayAtInstant: Option[Instant] =
    replayAt.map(Instant.parse)

object ReplayDomainEventOutboxRequest:
  given ReadWriter[ReplayDomainEventOutboxRequest] = macroRW

final case class AcknowledgeDomainEventOutboxRequest(
    operatorId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object AcknowledgeDomainEventOutboxRequest:
  given ReadWriter[AcknowledgeDomainEventOutboxRequest] = macroRW

final case class QuarantineDomainEventOutboxRequest(
    operatorId: String,
    reason: String
):
  def operator: PlayerId =
    PlayerId(operatorId)

object QuarantineDomainEventOutboxRequest:
  given ReadWriter[QuarantineDomainEventOutboxRequest] = macroRW

final case class BatchReplayDomainEventOutboxRequest(
    operatorId: String,
    recordIds: Vector[String],
    replayAt: Option[String] = None,
    note: Option[String] = None
):
  require(recordIds.nonEmpty, "Batch replay requires at least one recordId")

  def operator: PlayerId =
    PlayerId(operatorId)

  def records: Vector[DomainEventOutboxRecordId] =
    recordIds.map(DomainEventOutboxRecordId(_)).distinct

  def replayAtInstant: Option[Instant] =
    replayAt.map(Instant.parse)

object BatchReplayDomainEventOutboxRequest:
  given ReadWriter[BatchReplayDomainEventOutboxRequest] = macroRW

final case class BatchAcknowledgeDomainEventOutboxRequest(
    operatorId: String,
    recordIds: Vector[String],
    note: Option[String] = None
):
  require(recordIds.nonEmpty, "Batch acknowledge requires at least one recordId")

  def operator: PlayerId =
    PlayerId(operatorId)

  def records: Vector[DomainEventOutboxRecordId] =
    recordIds.map(DomainEventOutboxRecordId(_)).distinct

object BatchAcknowledgeDomainEventOutboxRequest:
  given ReadWriter[BatchAcknowledgeDomainEventOutboxRequest] = macroRW

final case class BatchQuarantineDomainEventOutboxRequest(
    operatorId: String,
    recordIds: Vector[String],
    reason: String
):
  require(recordIds.nonEmpty, "Batch quarantine requires at least one recordId")

  def operator: PlayerId =
    PlayerId(operatorId)

  def records: Vector[DomainEventOutboxRecordId] =
    recordIds.map(DomainEventOutboxRecordId(_)).distinct

object BatchQuarantineDomainEventOutboxRequest:
  given ReadWriter[BatchQuarantineDomainEventOutboxRequest] = macroRW
