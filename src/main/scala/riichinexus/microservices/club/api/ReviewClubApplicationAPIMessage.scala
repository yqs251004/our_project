package riichinexus.microservices.club.api
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import cats.syntax.all.*
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
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.audit.api.`private`.RecordAuditEventPrivateAPIMessage
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.notification.api.`private`.CreateNotificationPrivateAPIMessage
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.api.`private`.ClubApplicationViewAssembler
import riichinexus.microservices.club.domain.ClubApplicationReviewer
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicationView
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubApplicationReviewDecision, ReviewClubApplicationRequest}
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class ReviewClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    request: ReviewClubApplicationRequest
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      decision <- IO.blocking(resolveDecision(request.decision))
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(request.operatorId)).resolve(context.connection))
      reviewedAt <- IO.realTimeInstant
      command = ReviewClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        actor = actor,
        decision = decision,
        note = request.note,
        reviewedAt = reviewedAt
      )
      result <- IO.blocking(reviewApplication(context.connection, command))
      _ <- RecordAuditEventPrivateAPIMessage(reviewApplicationAudit(command)).plan(context)
      _ <- notifyApplicant(context, command, result)
    yield ClubApplicationViewAssembler.applicationView(
      context.connection,
      result.club,
      result.application,
      command.actor
    )

  private def reviewApplication(
      connection: java.sql.Connection,
      command: ReviewClubApplicationCommand
  ): ReviewClubApplicationResult =
    val reviewedClub = submitReview(connection, command)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    val reviewedApplication = ClubFunctions.findApplication(reviewedClub, command.membershipId).getOrElse(
      throw NoSuchElementException(
        s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
      )
    )
    ReviewClubApplicationResult(reviewedClub, reviewedApplication)

  private def submitReview(
      connection: java.sql.Connection,
      command: ReviewClubApplicationCommand
  ): Option[Club] =
    command.decision match
      case ApplicationReviewDecision.Approve =>
        val player = resolveApprovedPlayer(connection, command)
        ClubApplicationReviewer.approve(
          connection = connection,          parsedClubId = command.clubId,
          parsedMembershipId = command.membershipId,
          parsedPlayerId = player.id,
          actor = command.actor,
          note = command.note,
          approvedAt = command.reviewedAt
        )
      case ApplicationReviewDecision.Reject =>
        ClubApplicationReviewer.reject(
          connection = connection,          parsedClubId = command.clubId,
          parsedMembershipId = command.membershipId,
          actor = command.actor,
          note = command.note,
          rejectedAt = command.reviewedAt
        )

  private def resolveApprovedPlayer(
      connection: java.sql.Connection,
      command: ReviewClubApplicationCommand
  ): Player =
    val club = ClubTable
      .findById(connection, command.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    val application = ClubFunctions.findApplication(club, command.membershipId).getOrElse(
      throw NoSuchElementException(
        s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
      )
    )
    resolveApplicantPlayer(connection, application)
      .getOrElse(
        throw IllegalArgumentException(
          s"Membership application ${command.membershipId.value} applicant was not found"
        )
      )

  private def resolveApplicantPlayer(
      connection: java.sql.Connection,
      application: ClubMembershipApplication
  ): Option[Player] =
    application.playerId
      .flatMap(PlayerPersistenceFunctions.findPlayer(connection, _))
      .orElse(application.applicantUserId.flatMap(PlayerPersistenceFunctions.findPlayerByUserId(connection, _)))

  private def resolveDecision(decision: ClubApplicationReviewDecision): ApplicationReviewDecision =
    decision match
      case ClubApplicationReviewDecision.Approve => ApplicationReviewDecision.Approve
      case ClubApplicationReviewDecision.Reject  => ApplicationReviewDecision.Reject

  private def reviewApplicationAudit(command: ReviewClubApplicationCommand): AuditEvent =
    AuditEvent(
      id = AuditIdGenerator.auditEventId(),
      aggregateType = "club-application",
      aggregateId = command.clubId.value,
      eventType =
        command.decision match
          case ApplicationReviewDecision.Approve => "ClubApplicationApproved"
          case ApplicationReviewDecision.Reject  => "ClubApplicationRejected",
      occurredAt = command.reviewedAt,
      actorId = command.actor.playerId,
      details = Map(
        "clubId" -> command.clubId.value,
        "membershipId" -> command.membershipId.value
      )
    )

  private def notifyApplicant(
      context: ApiPlanContext,
      command: ReviewClubApplicationCommand,
      result: ReviewClubApplicationResult
  ): IO[Unit] =
    resolveApplicantRecipient(context.connection, command, result.application) match
      case Some(recipientPlayerId) =>
        CreateNotificationPrivateAPIMessage(
          reviewNotificationRequest(command, result, recipientPlayerId)
        ).plan(context).void
      case None => IO.unit

  private def resolveApplicantRecipient(
      connection: java.sql.Connection,
      command: ReviewClubApplicationCommand,
      application: ClubMembershipApplication
  ): Option[PlayerId] =
    resolveApplicantPlayer(connection, application).map(_.id)

  private def reviewNotificationRequest(
      command: ReviewClubApplicationCommand,
      result: ReviewClubApplicationResult,
      recipientPlayerId: PlayerId
  ): CreateNotificationRequest =
    val approved = command.decision == ApplicationReviewDecision.Approve
    val eventType = if approved then "ClubApplicationApproved" else "ClubApplicationRejected"
    val decisionLabel = if approved then "已通过" else "已拒绝"
    CreateNotificationRequest(
      recipientPlayerId = recipientPlayerId.value,
      notificationType = eventType,
      title = if approved then "俱乐部申请已通过" else "俱乐部申请已拒绝",
      body = s"你加入 ${result.club.name} 的申请$decisionLabel。",
      severity = Some(if approved then "success" else "info"),
      sourceService = "club",
      sourceType = "club-application",
      sourceId = result.application.id.value,
      actionUrl = Some(s"/public/clubs/${result.club.id.value}"),
      objects = Map(
        "clubId" -> result.club.id.value,
        "membershipId" -> result.application.id.value,
        "decision" -> decisionLabel
      )
    )

  private enum ApplicationReviewDecision:
    case Approve, Reject

  private final case class ReviewClubApplicationCommand(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      actor: AccessPrincipal,
      decision: ApplicationReviewDecision,
      note: Option[String],
      reviewedAt: Instant
  )

  private final case class ReviewClubApplicationResult(
      club: Club,
      application: ClubMembershipApplication
  )
