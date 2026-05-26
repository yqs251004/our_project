package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
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

object SettleTournamentRequest:
  given ReadWriter[SettleTournamentRequest] = macroRW

