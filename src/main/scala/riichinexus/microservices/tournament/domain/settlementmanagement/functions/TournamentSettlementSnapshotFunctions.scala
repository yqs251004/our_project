package riichinexus.microservices.tournament.domain.settlementmanagement.functions

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
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

private[tournament] object TournamentSettlementSnapshotFunctions:
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
