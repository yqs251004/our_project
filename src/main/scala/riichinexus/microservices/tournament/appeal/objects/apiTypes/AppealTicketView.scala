package riichinexus.microservices.tournament.appeal.objects.apiTypes

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
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket
import riichinexus.microservices.tournament.appeal.objects.{AppealDecisionLog, AppealPriority, AppealStatus}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class AppealTicketView(
    appealId: String,
    tableId: String,
    tournamentId: String,
    stageId: String,
    openedBy: String,
    description: String,
    attachments: Vector[AppealAttachmentView],
    priority: AppealPriority,
    assigneeId: Option[String],
    dueAt: Option[String],
    status: AppealStatus,
    logs: Vector[AppealDecisionLog],
    reopenCount: Int,
    createdAt: String,
    updatedAt: String,
    resolution: Option[String]
)

object AppealTicketView:
  def fromDomain(ticket: AppealTicket): AppealTicketView =
    AppealTicketView(
      appealId = ticket.id.value,
      tableId = ticket.tableId.value,
      tournamentId = ticket.tournamentId.value,
      stageId = ticket.stageId.value,
      openedBy = ticket.openedBy.value,
      description = ticket.description,
      attachments = ticket.attachments.map(AppealAttachmentView.fromDomain),
      priority = AppealPriority.fromDomain(ticket.priority),
      assigneeId = ticket.assigneeId.map(_.value),
      dueAt = ticket.dueAt.map(_.toString),
      status = AppealStatus.fromDomain(ticket.status),
      logs = ticket.logs,
      reopenCount = ticket.reopenCount,
      createdAt = ticket.createdAt.toString,
      updatedAt = ticket.updatedAt.toString,
      resolution = ticket.resolution
    )

  given ReadWriter[AppealTicketView] = macroRW
