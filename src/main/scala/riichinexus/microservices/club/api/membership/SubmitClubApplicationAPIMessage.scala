package riichinexus.microservices.club.api.membership

import riichinexus.system.objects.`private`.StructuredEventField

import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType}
import riichinexus.system.objects.`private`.AggregateType
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveRequestActorPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerBoundClubIdsPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.domain.profile.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.membership.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membership.model.ClubMembershipApplication
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.audit.api.`private`.RecordAuditEventPrivateAPIMessage
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest

import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.objects.membership.ClubApplicationStatus
import riichinexus.microservices.club.objects.membership.apiTypes.{ClubMembershipApplicationResponse, ClubMembershipApplicationRequest}
/** 提交加入俱乐部申请。 */
final case class SubmitClubApplicationAPIMessage(
    clubId: String,
    request: ClubMembershipApplicationRequest
) extends APIMessage[ClubMembershipApplicationResponse]:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationResponse] =
    for
      actor <- ResolveRequestActorPrivateAPIMessage(None, request.operatorId.map(PlayerId(_))).plan(context)
      requestedClubId = ClubId(clubId)
      submittedAt <- IO.realTimeInstant
      applicantInput <- resolveApplicantInput(context, actor, request)
      (applicantPlayerId, applicantDisplayName) = applicantInput
      applicantPlayer <- ResolvePlayerPrivateAPIMessage(applicantPlayerId).plan(context)
      applicantClubIds <- ResolvePlayerBoundClubIdsPrivateAPIMessage(applicantPlayerId).plan(context)
      _ <- RequirePermissionPrivateAPIMessage(actor, Permission.SubmitClubApplication).plan(context)
      submitted <- submitApplication(
        context,
        requestedClubId,
        applicantPlayerId,
        applicantDisplayName,
        request.message,
        submittedAt,
        applicantPlayer,
        applicantClubIds
      )
      (savedClub, application) = submitted
      _ <- RecordAuditEventPrivateAPIMessage(submitApplicationAudit(requestedClubId, actor, submittedAt, application)).plan(context)
      _ <- notifyClubAdmins(context, savedClub, application)
    yield applicationResponse(application)

  private def resolveApplicantInput(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      request: ClubMembershipApplicationRequest
  ): IO[(PlayerId, String)] =
    val playerId = actor.playerId
      .getOrElse(throw AuthorizationFailure("Only registered players can apply to clubs"))
    request.operatorId.filter(_.nonEmpty)
      .map(id => ResolvePlayerPrivateAPIMessage(PlayerId(id)).plan(context))
      .getOrElse(IO.pure(None))
      .map { operatorPlayer =>
        (playerId, operatorPlayer.map(_.nickname).getOrElse(request.displayName))
      }

  private def applicationResponse(application: ClubMembershipApplication): ClubMembershipApplicationResponse =
    ClubMembershipApplicationResponse(
      id = application.id.value,
      playerId = application.playerId.map(_.value),
      displayName = application.displayName,
      submittedAt = application.submittedAt.toString,
      message = application.message,
      status = ClubApplicationStatus.toString(application.status),
      reviewedBy = application.reviewedBy.map(_.value),
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId
    )

  private def submitApplication(
      context: ApiPlanContext,
      clubId: ClubId,
      applicantPlayerId: PlayerId,
      applicantDisplayName: String,
      message: Option[String],
      submittedAt: Instant,
      applicantPlayer: Option[PlayerPrivateView],
      applicantClubIds: Vector[ClubId]
  ): IO[(Club, ClubMembershipApplication)] =
    for
      club <- loadClub(context, clubId)
      _ <- IO.blocking(validateSubmission(club, clubId, applicantPlayerId, applicantDisplayName, applicantPlayer, applicantClubIds))
      application <- IO.blocking(createApplication(applicantPlayerId, applicantDisplayName, message, submittedAt))
      savedClub <- saveApplication(context, club, application)
    yield (savedClub, application)

  private def loadClub(context: ApiPlanContext, clubId: ClubId): IO[Club] =
    IO.blocking {
      riichinexus.microservices.club.tables.clubs.ClubTable
        .findById(context.connection, clubId)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }

  private def saveApplication(
      context: ApiPlanContext,
      club: Club,
      application: ClubMembershipApplication
  ): IO[Club] =
    IO.blocking {
      riichinexus.microservices.club.tables.clubs.ClubTable.save(
        context.connection,
        ClubFunctions.submitApplication(club, application)
      )
    }

  private def validateSubmission(
      club: Club,
      clubId: ClubId,
      applicantPlayerId: PlayerId,
      applicantDisplayName: String,
      applicantPlayer: Option[PlayerPrivateView],
      applicantClubIds: Vector[ClubId]
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ensureApplicationsOpen(club, clubId)
    ensureDisplayNameNonEmpty(applicantDisplayName)
    ensureNoPendingApplication(club, clubId, applicantPlayerId, applicantPlayer)
    ensureApplicantNotAlreadyMember(clubId, applicantPlayer, applicantClubIds)

  private def ensureApplicationsOpen(club: Club, clubId: ClubId): Unit =
    if !club.recruitmentPolicy.applicationsOpen then
      throw IllegalArgumentException(s"Club ${clubId.value} is not currently accepting membership applications")

  private def ensureDisplayNameNonEmpty(displayName: String): Unit =
    if displayName.trim.isEmpty then
      throw IllegalArgumentException("Membership application display name cannot be empty")

  private def ensureNoPendingApplication(
      club: Club,
      clubId: ClubId,
      playerId: PlayerId,
      applicantPlayer: Option[PlayerPrivateView]
  ): Unit =
    if club.membershipApplications.exists(application =>
        ClubMembershipApplicationFunctions.isPending(application) &&
          (application.playerId.contains(playerId) ||
            applicantPlayer.exists(existing => application.applicantUserId.contains(existing.userId)))
      )
    then
      throw IllegalArgumentException(
        s"PlayerPrivateView ${playerId.value} already has a pending application for club ${clubId.value}"
      )

  private def ensureApplicantNotAlreadyMember(
      clubId: ClubId,
      applicantPlayer: Option[PlayerPrivateView],
      applicantClubIds: Vector[ClubId]
  ): Unit =
    applicantPlayer.foreach { existingPlayer =>
      if applicantClubIds.contains(clubId) then
        throw IllegalArgumentException(
          s"PlayerPrivateView ${existingPlayer.id.value} is already a member of club ${clubId.value}"
        )
    }

  private def createApplication(
      applicantPlayerId: PlayerId,
      applicantDisplayName: String,
      message: Option[String],
      submittedAt: Instant
  ): ClubMembershipApplication =
    ClubMembershipApplication(
      id = ClubIdGenerator.membershipApplicationId(),
      playerId = Some(applicantPlayerId),
      displayName = applicantDisplayName,
      submittedAt = submittedAt,
      message = message
    )

  private def submitApplicationAudit(
      clubId: ClubId,
      actor: AccessPrincipalPrivateView,
      submittedAt: Instant,
      application: ClubMembershipApplication
  ): AuditEventDraft =
    AuditEventDraft(
      aggregateType = AggregateType.ClubApplication,
      aggregateId = clubId.value,
      eventType = AuditEventType.ClubApplicationSubmitted,
      occurredAt = submittedAt,
      actorId = actor.playerId,
      details = Map(
        StructuredEventField.toString(StructuredEventField.ClubId) -> clubId.value,
        StructuredEventField.toString(StructuredEventField.MembershipId) -> application.id.value,
        StructuredEventField.toString(StructuredEventField.DisplayName) -> application.displayName
      )
    )

  private def submitApplicationNotifications(
      club: Club,
      application: ClubMembershipApplication
  ): Vector[CreateNotificationRequest] =
    val recipients = (club.admins :+ club.creator).distinct

    recipients.map { recipient =>
      CreateNotificationRequest(
        recipientPlayerId = recipient.value,
        notificationType = NotificationType.ClubApplicationSubmitted,
        title = "新的俱乐部申请",
        body = s"${application.displayName} 提交了加入 ${club.name} 的申请。",
        severity = Some(NotificationSeverity.Info),
        sourceService = NotificationSourceService.Club,
        sourceType = NotificationSourceType.ClubApplication,
        sourceId = application.id.value,
        actionUrl = Some(s"/public/clubs/${club.id.value}"),
        objects = Map(
          StructuredEventField.toString(StructuredEventField.ClubId) -> club.id.value,
          StructuredEventField.toString(StructuredEventField.MembershipId) -> application.id.value,
          StructuredEventField.toString(StructuredEventField.DisplayName) -> application.displayName
        )
      )
    }

  private def notifyClubAdmins(
      context: ApiPlanContext,
      club: Club,
      application: ClubMembershipApplication
  ): IO[Unit] =
    RecordBulkNotificationsPrivateAPIMessage(
      submitApplicationNotifications(club, application)
    ).plan(context).void
