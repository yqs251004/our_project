package riichinexus.microservices.tournament.domain

import java.time.Instant

import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.domain.model.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.model.{
  TournamentSettlementAdjustment
}

final case class SettleTournamentCommand(
    tournamentId: TournamentId,
    finalStageId: TournamentStageId,
    actor: AccessPrincipal,
    settledAt: Instant,
    prizePool: Long,
    payoutRatios: Vector[Double],
    houseFeeAmount: Long,
    clubShareRatio: Double,
    adjustments: Vector[TournamentSettlementAdjustment],
    finalizeSettlement: Boolean,
    note: Option[String]
)
