package riichinexus.microservices.tournament.api.requests

import riichinexus.domain.model.*
import upickle.default.*

final case class SettleTournamentRequest(
    operatorId: String,
    finalStageId: String,
    prizePool: Long = 0L,
    payoutRatios: Vector[Double] = Vector.empty,
    houseFeeAmount: Long = 0L,
    clubShareRatio: Double = 0.0,
    adjustments: Vector[SettlementAdjustmentRequest] = Vector.empty,
    finalizeSettlement: Boolean = true,
    note: Option[String] = None
):
  require(houseFeeAmount >= 0L, "Tournament settlement houseFeeAmount must be non-negative")
  require(clubShareRatio >= 0.0 && clubShareRatio <= 1.0, "Tournament settlement clubShareRatio must be between 0.0 and 1.0")

  def operator: PlayerId =
    PlayerId(operatorId)

  def stageId: TournamentStageId =
    TournamentStageId(finalStageId)

final case class SettlementAdjustmentRequest(
    playerId: String,
    label: String,
    amount: Long,
    note: Option[String] = None
):
  def adjustment: TournamentSettlementAdjustment =
    TournamentSettlementAdjustment(
      playerId = PlayerId(playerId),
      label = label,
      amount = amount,
      note = note
    )

final case class FinalizeTournamentSettlementRequest(
    operatorId: String,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

object SettlementRequests:
  given ReadWriter[SettlementAdjustmentRequest] = macroRW
  given ReadWriter[SettleTournamentRequest] = macroRW
  given ReadWriter[FinalizeTournamentSettlementRequest] = macroRW
