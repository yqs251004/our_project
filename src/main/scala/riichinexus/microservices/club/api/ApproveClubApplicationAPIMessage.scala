package riichinexus.microservices.club.api
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
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
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubApplicationReviewer
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import upickle.default.*

final case class ApproveClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    playerId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipal(PlayerId(operatorId)).plan(context)
      approvedAt <- IO.realTimeInstant
      command = ApproveClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        playerId = PlayerId(playerId),
        actor = actor,
        note = note,
        approvedAt = approvedAt
      )
      club <- approveApplication(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield ClubView.fromDomain(club)

  private def approveApplication(
      context: ApiPlanContext,
      command: ApproveClubApplicationCommand
  ): IO[Option[Club]] =
    ClubApplicationReviewer.approve(
      context = context,      parsedClubId = command.clubId,
      parsedMembershipId = command.membershipId,
      parsedPlayerId = command.playerId,
      actor = command.actor,
      note = command.note,
      approvedAt = command.approvedAt
    )

  private final case class ApproveClubApplicationCommand(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String],
      approvedAt: Instant
  )
