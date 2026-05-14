package riichinexus.microservices.opsanalytics.api.requests

import riichinexus.domain.model.{AdvancedStatsBackfillMode, PlayerId}
import upickle.default.*

final case class RecomputeAdvancedStatsRequest(
    operatorId: String,
    mode: String = AdvancedStatsBackfillMode.Full.toString,
    ownerType: Option[String] = None,
    ownerId: Option[String] = None,
    reason: Option[String] = None,
    limit: Int = 500
):
  require(ownerType.nonEmpty == ownerId.nonEmpty, "ownerType and ownerId must be provided together")
  require(limit > 0, "Advanced stats recompute limit must be positive")

  def operator: PlayerId =
    PlayerId(operatorId)

  def parsedMode: AdvancedStatsBackfillMode =
    AdvancedStatsBackfillMode.valueOf(mode)

object RecomputeAdvancedStatsRequest:
  given ReadWriter[RecomputeAdvancedStatsRequest] = macroRW

final case class ProcessAdvancedStatsTasksRequest(
    operatorId: String,
    limit: Int = 50
):
  require(limit > 0, "Advanced stats task processing limit must be positive")

  def operator: PlayerId =
    PlayerId(operatorId)

object ProcessAdvancedStatsTasksRequest:
  given ReadWriter[ProcessAdvancedStatsTasksRequest] = macroRW
