package riichinexus.microservices.tournament.domain.settlementmanagement.functions

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import java.time.Instant

import riichinexus.microservices.tournament.domain.settlementmanagement.model.TournamentSettlementSnapshot
import riichinexus.microservices.tournament.objects.settlementmanagement.TournamentSettlementStatus

object TournamentSettlementSnapshotFunctions:
  def validate(snapshot: TournamentSettlementSnapshot): Unit =
    require(snapshot.revision > 0, "Tournament settlement revision must be positive")
    require(snapshot.prizePool >= 0L, "Tournament settlement prize pool must be non-negative")
    require(snapshot.houseFeeAmount >= 0L, "Tournament settlement houseFeeAmount must be non-negative")
    require(snapshot.netPrizePool >= 0L, "Tournament settlement netPrizePool must be non-negative")
    require(snapshot.houseFeeAmount <= snapshot.prizePool, "Tournament settlement houseFeeAmount cannot exceed prizePool")
    require(
      snapshot.clubShareRatio >= 0.0 && snapshot.clubShareRatio <= 1.0,
      "Tournament settlement clubShareRatio must be between 0.0 and 1.0"
    )

  def finalize(snapshot: TournamentSettlementSnapshot, at: Instant): TournamentSettlementSnapshot =
    require(snapshot.status == TournamentSettlementStatus.Draft, "Only draft tournament settlements can be finalized")
    snapshot.copy(
      status = TournamentSettlementStatus.Finalized,
      finalizedAt = Some(at)
    )

  def supersede(snapshot: TournamentSettlementSnapshot, at: Instant): TournamentSettlementSnapshot =
    require(snapshot.status != TournamentSettlementStatus.Superseded, "Tournament settlement is already superseded")
    snapshot.copy(
      status = TournamentSettlementStatus.Superseded,
      supersededAt = Some(at)
    )
