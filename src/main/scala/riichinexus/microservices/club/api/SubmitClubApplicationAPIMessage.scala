package riichinexus.microservices.club.api
import riichinexus.microservices.club.domain.functions.ClubViewFunctions
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveRequestActorPrivateAPIMessage
import riichinexus.microservices.auth.api.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{ResolvePlayerBoundClubIdsPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMembershipApplication
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.audit.api.`private`.RecordAuditEventPrivateAPIMessage
import riichinexus.microservices.audit.objects.`private`.AuditEventDraft
import riichinexus.microservices.notification.api.`private`.RecordBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest

import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubMembershipApplicationResponse, ClubMembershipApplicationRequest}
/** 提交加入俱乐部申请。 */
final case class SubmitClubApplicationAPIMessage(
    clubId: String,
    request: ClubMembershipApplicationRequest
) extends APIMessage[ClubMembershipApplicationResponse]:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationResponse] =
    for
      actor <- ResolveRequestActorPrivateAPIMessage(None, request.operatorId.map(PlayerId(_))).plan(context)
      parsedClubId = ClubId(clubId)
      submittedAt <- IO.realTimeInstant
      resolvedInput <- resolveApplicantInput(context, actor, request)
      command = SubmitClubApplicationCommand(
        actor = actor,
        clubId = parsedClubId,
        submittedAt = submittedAt,
        input = resolvedInput,
        message = request.message
      )
      applicantPlayer <- ResolvePlayerPrivateAPIMessage(command.input.playerId).plan(context)
      applicantClubIds <- ResolvePlayerBoundClubIdsPrivateAPIMessage(command.input.playerId).plan(context)
      _ <- RequirePermissionPrivateAPIMessage(command.actor, Permission.SubmitClubApplication).plan(context)
      result <- submitApplication(context, command, applicantPlayer, applicantClubIds)
      _ <- RecordAuditEventPrivateAPIMessage(submitApplicationAudit(command, result.application)).plan(context)
      _ <- notifyClubAdmins(context, result)
    yield ClubViewFunctions.membershipApplicationResponse(result.application)

  private def resolveApplicantInput(
      context: ApiPlanContext,
      actor: AccessPrincipalPrivateView,
      request: ClubMembershipApplicationRequest
  ): IO[ResolvedClubApplicationInput] =
    val playerId = actor.playerId
      .getOrElse(throw AuthorizationFailure("Only registered players can apply to clubs"))
    request.operatorId.filter(_.nonEmpty)
      .map(id => ResolvePlayerPrivateAPIMessage(PlayerId(id)).plan(context))
      .getOrElse(IO.pure(None))
      .map { operatorPlayer =>
        ResolvedClubApplicationInput(
          playerId = playerId,
          displayName = operatorPlayer.map(_.nickname).getOrElse(request.displayName)
        )
      }

  private def submitApplication(
      context: ApiPlanContext,
      command: SubmitClubApplicationCommand,
      applicantPlayer: Option[PlayerPrivateView],
      applicantClubIds: Vector[ClubId]
  ): IO[SubmitClubApplicationResult] =
    for
      club <- loadClub(context, command.clubId)
      _ <- IO.blocking(validateSubmission(club, command, applicantPlayer, applicantClubIds))
      application <- IO.blocking(createApplication(command))
      savedClub <- saveApplication(context, club, application)
    yield SubmitClubApplicationResult(savedClub, application)

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
      command: SubmitClubApplicationCommand,
      applicantPlayer: Option[PlayerPrivateView],
      applicantClubIds: Vector[ClubId]
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ensureApplicationsOpen(club, command.clubId)
    ensureDisplayNameNonEmpty(command.input.displayName)
    ensureNoPendingApplication(club, command, applicantPlayer)
    ensureApplicantNotAlreadyMember(command, applicantPlayer, applicantClubIds)

  private def ensureApplicationsOpen(club: Club, clubId: ClubId): Unit =
    if !club.recruitmentPolicy.applicationsOpen then
      throw IllegalArgumentException(s"Club ${clubId.value} is not currently accepting membership applications")

  private def ensureDisplayNameNonEmpty(displayName: String): Unit =
    if displayName.trim.isEmpty then
      throw IllegalArgumentException("Membership application display name cannot be empty")

  private def ensureNoPendingApplication(
      club: Club,
      command: SubmitClubApplicationCommand,
      applicantPlayer: Option[PlayerPrivateView]
  ): Unit =
    val playerId = command.input.playerId
    if club.membershipApplications.exists(application =>
        ClubMembershipApplicationFunctions.isPending(application) &&
          (application.playerId.contains(playerId) ||
            applicantPlayer.exists(existing => application.applicantUserId.contains(existing.userId)))
      )
    then
      throw IllegalArgumentException(
        s"PlayerPrivateView ${playerId.value} already has a pending application for club ${command.clubId.value}"
      )

  private def ensureApplicantNotAlreadyMember(
      command: SubmitClubApplicationCommand,
      applicantPlayer: Option[PlayerPrivateView],
      applicantClubIds: Vector[ClubId]
  ): Unit =
    applicantPlayer.foreach { existingPlayer =>
      if applicantClubIds.contains(command.clubId) then
        throw IllegalArgumentException(
          s"PlayerPrivateView ${existingPlayer.id.value} is already a member of club ${command.clubId.value}"
        )
    }

  private def createApplication(command: SubmitClubApplicationCommand): ClubMembershipApplication =
    ClubMembershipApplication(
      id = ClubIdGenerator.membershipApplicationId(),
      playerId = Some(command.input.playerId),
      displayName = command.input.displayName,
      submittedAt = command.submittedAt,
      message = command.message
    )

  private def submitApplicationAudit(
      command: SubmitClubApplicationCommand,
      application: ClubMembershipApplication
  ): AuditEventDraft =
    AuditEventDraft(
      aggregateType = "club-application",
      aggregateId = command.clubId.value,
      eventType = AuditEventType.ClubApplicationSubmitted,
      occurredAt = command.submittedAt,
      actorId = command.actor.playerId,
      details = Map(
        "clubId" -> command.clubId.value,
        "membershipId" -> application.id.value,
        "displayName" -> application.displayName
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
        severity = Some("info"),
        sourceService = "club",
        sourceType = "club-application",
        sourceId = application.id.value,
        actionUrl = Some(s"/public/clubs/${club.id.value}"),
        objects = Map(
          "clubId" -> club.id.value,
          "membershipId" -> application.id.value,
          "displayName" -> application.displayName
        )
      )
    }

  private def notifyClubAdmins(
      context: ApiPlanContext,
      result: SubmitClubApplicationResult
  ): IO[Unit] =
    RecordBulkNotificationsPrivateAPIMessage(
      submitApplicationNotifications(result.club, result.application)
    ).plan(context).void

  private final case class SubmitClubApplicationCommand(
      actor: AccessPrincipalPrivateView,
      clubId: ClubId,
      submittedAt: Instant,
      input: ResolvedClubApplicationInput,
      message: Option[String]
  )

  private final case class SubmitClubApplicationResult(
      club: Club,
      application: ClubMembershipApplication
  )

  private final case class ResolvedClubApplicationInput(
      playerId: PlayerId,
      displayName: String
  )
