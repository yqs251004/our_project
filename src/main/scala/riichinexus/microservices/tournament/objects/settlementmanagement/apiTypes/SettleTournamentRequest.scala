package riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
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
)

object SettleTournamentRequest:
  given ReadWriter[SettleTournamentRequest] = macroRW
