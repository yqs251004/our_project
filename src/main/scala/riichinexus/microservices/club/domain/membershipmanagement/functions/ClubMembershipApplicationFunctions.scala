package riichinexus.microservices.club.domain.membershipmanagement.functions

import java.time.Instant

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
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMembershipApplication
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus

private[club] object ClubMembershipApplicationFunctions:
  def isPending(application: ClubMembershipApplication): Boolean =
    application.status == ClubApplicationStatus.Pending

  def approve(
      application: ClubMembershipApplication,
      by: PlayerId,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be approved")
    application.copy(
      status = ClubApplicationStatus.Approved,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def reject(
      application: ClubMembershipApplication,
      by: PlayerId,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be rejected")
    application.copy(
      status = ClubApplicationStatus.Rejected,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def withdraw(
      application: ClubMembershipApplication,
      byPrincipalId: String,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be withdrawn")
    application.copy(
      status = ClubApplicationStatus.Withdrawn,
      reviewedAt = Some(at),
      reviewNote = note,
      withdrawnByPrincipalId = Some(byPrincipalId)
    )

