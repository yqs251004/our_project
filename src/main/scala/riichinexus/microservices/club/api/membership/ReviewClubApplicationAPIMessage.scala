package riichinexus.microservices.club.api.membership

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType}
import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.auth.api.authorization.`private`.CheckSuperAdminPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerBoundClubIdsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerByUserIdPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.objects.membership.MembershipApplicationId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.membership.model.ClubMembershipApplication
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.audit.api.`private`.RecordAuditEventPrivateAPIMessage
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.notification.api.`private`.RecordNotificationPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest

import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.domain.membership.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.objects.membership.ClubApplicationStatus
import riichinexus.microservices.club.objects.membership.ClubMembershipApplicantView
import riichinexus.microservices.club.domain.membership.functions.ClubApplicationReviewer
import riichinexus.microservices.club.objects.membership.ClubMembershipApplicationView
import riichinexus.microservices.club.objects.membership.apiTypes.{ReviewClubApplicationRequest}
import riichinexus.microservices.club.objects.membership.{ClubApplicationReviewDecision}
import riichinexus.microservices.club.tables.clubs.ClubTable
/** 审核俱乐部申请。 */
final case class ReviewClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    request: ReviewClubApplicationRequest
) extends APIMessage[ClubMembershipApplicationView]:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(request.operatorId)).plan(context)
      reviewedAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      requestedMembershipId = MembershipApplicationId(membershipId)
      reviewed <- reviewApplication(context, requestedClubId, requestedMembershipId, actor, request.decision, request.note, reviewedAt)
      (reviewedClub, reviewedApplication) = reviewed
      _ <- RecordAuditEventPrivateAPIMessage(reviewApplicationAudit(requestedClubId, requestedMembershipId, actor, request.decision, reviewedAt)).plan(context)
      _ <- notifyApplicant(context, reviewedClub, reviewedApplication, request.decision)
      view <- applicationView(context, reviewedClub, reviewedApplication, actor)
    yield view

  private def reviewApplication(
      context: ApiPlanContext,
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      actor: AccessPrincipalPrivateView,
      decision: ClubApplicationReviewDecision,
      note: Option[String],
      reviewedAt: Instant
  ): IO[(Club, ClubMembershipApplication)] =
    for
      reviewedClub <- submitReview(context, clubId, membershipId, actor, decision, note, reviewedAt)
        .map(_.getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found")))
      reviewedApplication <- IO.blocking {
        ClubFunctions.findApplication(reviewedClub, membershipId).getOrElse(
          throw NoSuchElementException(
            s"Membership application ${membershipId.value} was not found in club ${clubId.value}"
          )
        )
      }
    yield (reviewedClub, reviewedApplication)

  private def submitReview(
      context: ApiPlanContext,
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      actor: AccessPrincipalPrivateView,
      decision: ClubApplicationReviewDecision,
      note: Option[String],
      reviewedAt: Instant
  ): IO[Option[Club]] =
    decision match
      case ClubApplicationReviewDecision.Approve =>
        for
          player <- resolveApprovedPlayer(context, clubId, membershipId)
          reviewedClub <- ClubApplicationReviewer.approve(
            context = context,
            clubId = clubId,
            membershipApplicationId = membershipId,
            applicantPlayerId = player.id,
            actor = actor,
            note = note,
            approvedAt = reviewedAt
          )
        yield reviewedClub
      case ClubApplicationReviewDecision.Reject =>
        ClubApplicationReviewer.reject(
          context = context,
          clubId = clubId,
          membershipApplicationId = membershipId,
          actor = actor,
          note = note,
          rejectedAt = reviewedAt
        )

  private def resolveApprovedPlayer(
      context: ApiPlanContext,
      clubId: ClubId,
      membershipId: MembershipApplicationId
  ): IO[PlayerPrivateView] =
    for
      application <- IO.blocking {
        val club = ClubTable
          .findById(context.connection, clubId)
          .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))
        ClubFunctions.findApplication(club, membershipId).getOrElse(
          throw NoSuchElementException(
            s"Membership application ${membershipId.value} was not found in club ${clubId.value}"
          )
        )
      }
      player <- resolveApplicantPlayer(context, application).map(
        _.getOrElse(
          throw IllegalArgumentException(
            s"Membership application ${membershipId.value} applicant was not found"
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

  private def reviewApplicationAudit(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      actor: AccessPrincipalPrivateView,
      decision: ClubApplicationReviewDecision,
      reviewedAt: Instant
  ): AuditEventDraft =
    AuditEventDraft(
      aggregateType = AggregateType.ClubApplication,
      aggregateId = clubId.value,
      eventType =
        decision match
          case ClubApplicationReviewDecision.Approve => AuditEventType.ClubApplicationApproved
          case ClubApplicationReviewDecision.Reject  => AuditEventType.ClubApplicationRejected,
      occurredAt = reviewedAt,
      actorId = actor.playerId,
      details = Map(
        StructuredEventField.toString(StructuredEventField.ClubId) -> clubId.value,
        StructuredEventField.toString(StructuredEventField.MembershipId) -> membershipId.value
      )
    )

  private def notifyApplicant(
      context: ApiPlanContext,
      club: Club,
      application: ClubMembershipApplication,
      decision: ClubApplicationReviewDecision
  ): IO[Unit] =
    for
      recipient <- resolveApplicantRecipient(context, application)
      _ <- recipient match
        case Some(recipientPlayerId) =>
          RecordNotificationPrivateAPIMessage(
            reviewNotificationRequest(club, application, decision, recipientPlayerId)
          ).plan(context).void
        case None => IO.unit
    yield ()

  private def resolveApplicantRecipient(
      context: ApiPlanContext,
      application: ClubMembershipApplication
  ): IO[Option[PlayerId]] =
    resolveApplicantPlayer(context, application).map(_.map(_.id))

  private def reviewNotificationRequest(
      club: Club,
      application: ClubMembershipApplication,
      decision: ClubApplicationReviewDecision,
      recipientPlayerId: PlayerId
  ): CreateNotificationRequest =
    val approved = decision == ClubApplicationReviewDecision.Approve
    val notificationType =
      if approved then NotificationType.ClubApplicationApproved
      else NotificationType.ClubApplicationRejected
    val decisionLabel = if approved then "已通过" else "已拒绝"
    CreateNotificationRequest(
      recipientPlayerId = recipientPlayerId.value,
      notificationType = notificationType,
      title = if approved then "俱乐部申请已通过" else "俱乐部申请已拒绝",
      body = s"你加入 ${club.name} 的申请$decisionLabel。",
      severity = Some(if approved then NotificationSeverity.Success else NotificationSeverity.Info),
      sourceService = NotificationSourceService.Club,
      sourceType = NotificationSourceType.ClubApplication,
      sourceId = application.id.value,
      actionUrl = Some(s"/public/clubs/${club.id.value}"),
      objects = Map(
        StructuredEventField.toString(StructuredEventField.ClubId) -> club.id.value,
        StructuredEventField.toString(StructuredEventField.MembershipId) -> application.id.value,
        StructuredEventField.toString(StructuredEventField.Decision) -> decisionLabel
      )
    )
