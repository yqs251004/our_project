package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.{CheckSuperAdminPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.player.api.`private`.{ResolvePlayerBoundClubIdsPrivateAPIMessage, ResolvePlayerByUserIdPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMembershipApplication
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.audit.api.`private`.RecordAuditEventPrivateAPIMessage
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.notification.api.`private`.RecordNotificationPrivateAPIMessage
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest

import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicantView
import riichinexus.microservices.club.domain.ClubApplicationReviewer
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicationView
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubApplicationReviewDecision, ReviewClubApplicationRequest}
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.ReadWriter

/** 审核俱乐部申请。 */
final case class ReviewClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    request: ReviewClubApplicationRequest
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      decision <- IO.pure(resolveDecision(request.decision))
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
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
      view <- applicationView(
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
        for
          player <- resolveApprovedPlayer(context, command)
          reviewedClub <- ClubApplicationReviewer.approve(
            context = context,
            parsedClubId = command.clubId,
            parsedMembershipId = command.membershipId,
            parsedPlayerId = player.id,
            actor = command.actor,
            note = command.note,
            approvedAt = command.reviewedAt
          )
        yield reviewedClub
      case ApplicationReviewDecision.Reject =>
        ClubApplicationReviewer.reject(
          context = context,
          parsedClubId = command.clubId,
          parsedMembershipId = command.membershipId,
          actor = command.actor,
          note = command.note,
          rejectedAt = command.reviewedAt
        )

  private def resolveApprovedPlayer(
      context: ApiPlanContext,
      command: ReviewClubApplicationCommand
  ): IO[PlayerPrivateView] =
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
  ): IO[Option[PlayerPrivateView]] =
    application.playerId match
      case Some(playerId) => ResolvePlayerPrivateAPIMessage(playerId).plan(context)
      case None =>
        application.applicantUserId
          .map(ResolvePlayerByUserIdPrivateAPIMessage(_).plan(context))
          .getOrElse(IO.pure(None))

  private def applicationView(
      context: ApiPlanContext,
      club: Club,
      application: ClubMembershipApplication,
      actor: AccessPrincipalPrivateView
  ): IO[ClubMembershipApplicationView] =
    for
      applicantPlayer <- resolveApplicantPlayer(context, application)
      applicantClubIds <- applicantPlayer
        .map(player => ResolvePlayerBoundClubIdsPrivateAPIMessage(player.id).plan(context).map(_.map(_.value)))
        .getOrElse(IO.pure(Vector.empty))
      reviewedByDisplayName <- application.reviewedBy
        .map(playerId => ResolvePlayerPrivateAPIMessage(playerId).plan(context).map(_.map(_.nickname)))
        .getOrElse(IO.pure(None))
      canWithdraw <- canWithdrawClubApplication(context, actor, application)
    yield ClubMembershipApplicationView(
      applicationId = application.id.value,
      clubId = club.id.value,
      clubName = club.name,
      applicant = ClubMembershipApplicantView(
        playerId = applicantPlayer.map(_.id.value),
        displayName = application.displayName,
        playerStatus = applicantPlayer.map(_.status.toString),
        currentRank = applicantPlayer.map(_.currentRank),
        elo = applicantPlayer.map(_.elo),
        clubIds = applicantClubIds
      ),
      submittedAt = application.submittedAt.toString,
      message = application.message,
      status = ClubApplicationStatus.toString(application.status),
      reviewedBy = application.reviewedBy.map(_.value),
      reviewedByDisplayName = reviewedByDisplayName,
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId,
      canReview = ClubMembershipApplicationFunctions.isPending(application) && ClubAuthorization.canManageClubApplications(actor, club),
      canWithdraw = ClubMembershipApplicationFunctions.isPending(application) && canWithdraw
    )

  private def canWithdrawClubApplication(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      application: ClubMembershipApplication
  ): IO[Boolean] =
    for
      isSuperAdmin <- CheckSuperAdminPrivateAPIMessage(actor).plan(context)
      canWithdraw <-
        if isSuperAdmin then IO.pure(true)
        else ownsClubApplication(context, actor, application)
    yield canWithdraw

  private def ownsClubApplication(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      application: ClubMembershipApplication
  ): IO[Boolean] =
    actor.playerId
      .map(playerId => ResolvePlayerPrivateAPIMessage(playerId).plan(context))
      .getOrElse(IO.pure(None))
      .map(_.exists { player =>
        application.playerId.contains(player.id) ||
          application.applicantUserId.contains(player.userId)
      })

  private def resolveDecision(decision: ClubApplicationReviewDecision): ApplicationReviewDecision =
    decision match
      case ClubApplicationReviewDecision.Approve => ApplicationReviewDecision.Approve
      case ClubApplicationReviewDecision.Reject  => ApplicationReviewDecision.Reject

  private def reviewApplicationAudit(command: ReviewClubApplicationCommand): AuditEventDraft =
    AuditEventDraft(
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
    for
      recipient <- resolveApplicantRecipient(context, command, result.application)
      _ <- recipient match
        case Some(recipientPlayerId) =>
          RecordNotificationPrivateAPIMessage(
            reviewNotificationRequest(command, result, recipientPlayerId)
          ).plan(context).void
        case None => IO.unit
    yield ()

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
      actor: AccessPrincipalPrivateView,
      decision: ApplicationReviewDecision,
      note: Option[String],
      reviewedAt: Instant
  )

  private final case class ReviewClubApplicationResult(
      club: Club,
      application: ClubMembershipApplication
  )
