package riichinexus.microservices.club.api
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.*

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
      actor <- ResolveAccessPrincipal(PlayerId(request.operatorId)).plan(context)
      reviewedAt <- IO.realTimeInstant
      command = ReviewClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        actor = actor,
        decision = decision,
        note = request.note,
        reviewedAt = reviewedAt
      )
      result <- reviewApplication(context, command)
      _ <- RecordAuditEventPrivateAPIMessage(reviewApplicationAudit(command)).plan(context)
      _ <- notifyApplicant(context, command, result)
      view <- ClubApplicationViewAssembler.applicationView(
        context,
        result.club,
        result.application,
        command.actor
      )
    yield view

  private def reviewApplication(
      context: ApiPlanContext,
      command: ReviewClubApplicationCommand
  ): IO[ReviewClubApplicationResult] =
    for
      reviewedClub <- submitReview(context, command)
        .map(_.getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found")))
      reviewedApplication <- IO.blocking {
        ClubFunctions.findApplication(reviewedClub, command.membershipId).getOrElse(
          throw NoSuchElementException(
            s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
          )
        )
      }
    yield ReviewClubApplicationResult(reviewedClub, reviewedApplication)

  private def submitReview(
      context: ApiPlanContext,
      command: ReviewClubApplicationCommand
  ): IO[Option[Club]] =
    command.decision match
      case ApplicationReviewDecision.Approve =>
        resolveApprovedPlayer(context, command).flatMap { player =>
          ClubApplicationReviewer.approve(
            context = context,          parsedClubId = command.clubId,
            parsedMembershipId = command.membershipId,
            parsedPlayerId = player.id,
            actor = command.actor,
            note = command.note,
            approvedAt = command.reviewedAt
          )
        }
      case ApplicationReviewDecision.Reject =>
        ClubApplicationReviewer.reject(
          context = context,          parsedClubId = command.clubId,
          parsedMembershipId = command.membershipId,
          actor = command.actor,
          note = command.note,
          rejectedAt = command.reviewedAt
        )

  private def resolveApprovedPlayer(
      context: ApiPlanContext,
      command: ReviewClubApplicationCommand
  ): IO[Player] =
    for
      application <- IO.blocking {
        val club = ClubTable
          .findById(context.connection, command.clubId)
          .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
        ClubFunctions.findApplication(club, command.membershipId).getOrElse(
          throw NoSuchElementException(
            s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
          )
        )
      }
      player <- resolveApplicantPlayer(context, application).map(
        _.getOrElse(
          throw IllegalArgumentException(
            s"Membership application ${command.membershipId.value} applicant was not found"
          )
        )
      )
    yield player

  private def resolveApplicantPlayer(
      context: ApiPlanContext,
      application: ClubMembershipApplication
  ): IO[Option[Player]] =
    application.playerId match
      case Some(playerId) => ResolvePlayerPrivateAPIMessage(playerId).plan(context)
      case None =>
        application.applicantUserId
          .map(ResolvePlayerByUserIdPrivateAPIMessage(_).plan(context))
          .getOrElse(IO.pure(None))

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
    resolveApplicantRecipient(context, command, result.application).flatMap {
      case Some(recipientPlayerId) =>
        CreateNotificationPrivateAPIMessage(
          reviewNotificationRequest(command, result, recipientPlayerId)
        ).plan(context).void
      case None => IO.unit
    }

  private def resolveApplicantRecipient(
      context: ApiPlanContext,
      command: ReviewClubApplicationCommand,
      application: ClubMembershipApplication
  ): IO[Option[PlayerId]] =
    resolveApplicantPlayer(context, application).map(_.map(_.id))

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
