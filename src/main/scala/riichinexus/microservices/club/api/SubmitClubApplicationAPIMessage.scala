package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.*

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
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
import riichinexus.microservices.club.domain.membershipmanagement.functions.ClubMembershipApplicationFunctions
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.domain.functions.PlayerClubBindingFunctions
import riichinexus.microservices.auth.domain.*
import riichinexus.microservices.audit.api.`private`.RecordAuditEventPrivateAPIMessage
import riichinexus.microservices.audit.domain.auditevent.AuditEvent
import riichinexus.microservices.notification.api.`private`.CreateBulkNotificationsPrivateAPIMessage
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.{ClubMembershipApplicationResponse, ClubMembershipApplicationRequest}
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class SubmitClubApplicationAPIMessage(
    clubId: String,
    request: ClubMembershipApplicationRequest
) extends APIMessage[ClubMembershipApplicationResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationResponse] =
    for
      actor <- ResolveRequestActor(None, request.operatorId.map(PlayerId(_))).plan(context)
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
      application <- submitApplication(context, command)
      _ <- RecordAuditEventPrivateAPIMessage(submitApplicationAudit(command, application)).plan(context)
      notificationRequests <- IO.blocking(submitApplicationNotifications(context.connection, command, application))
      _ <- CreateBulkNotificationsPrivateAPIMessage(notificationRequests).plan(context)
    yield ClubMembershipApplicationResponse.fromDomain(application)

  private def resolveApplicantInput(
      context: ApiPlanContext,
      actor: AccessPrincipal,
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
      command: SubmitClubApplicationCommand
  ): IO[ClubMembershipApplication] =
    val connection = context.connection
    for
      applicantPlayer <- ResolvePlayerPrivateAPIMessage(command.input.playerId).plan(context)
      application <- IO.blocking {
        AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, command.actor, Permission.SubmitClubApplication)
        val club = riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId)
          .getOrElse(throw NoSuchElementException("Resource not found"))
        validateSubmission(club, command, applicantPlayer)
        val application = createApplication(command)
        riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.submitApplication(club, application))
        application
      }
    yield application

  private def validateSubmission(
      club: Club,
      command: SubmitClubApplicationCommand,
      applicantPlayer: Option[Player]
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ensureApplicationsOpen(club, command.clubId)
    ensureDisplayNameNonEmpty(command.input.displayName)
    ensureNoPendingApplication(club, command, applicantPlayer)
    ensureApplicantNotAlreadyMember(command, applicantPlayer)

  private def ensureApplicationsOpen(club: Club, clubId: ClubId): Unit =
    if !club.recruitmentPolicy.applicationsOpen then
      throw IllegalArgumentException(s"Club ${clubId.value} is not currently accepting membership applications")

  private def ensureDisplayNameNonEmpty(displayName: String): Unit =
    if displayName.trim.isEmpty then
      throw IllegalArgumentException("Membership application display name cannot be empty")

  private def ensureNoPendingApplication(
      club: Club,
      command: SubmitClubApplicationCommand,
      applicantPlayer: Option[Player]
  ): Unit =
    val playerId = command.input.playerId
    if club.membershipApplications.exists(application =>
        ClubMembershipApplicationFunctions.isPending(application) &&
          (application.playerId.contains(playerId) ||
            applicantPlayer.exists(existing => application.applicantUserId.contains(existing.userId)))
      )
    then
      throw IllegalArgumentException(
        s"Player ${playerId.value} already has a pending application for club ${command.clubId.value}"
      )

  private def ensureApplicantNotAlreadyMember(
      command: SubmitClubApplicationCommand,
      applicantPlayer: Option[Player]
  ): Unit =
    applicantPlayer.foreach { existingPlayer =>
      if PlayerClubBindingFunctions.boundClubIds(existingPlayer).contains(command.clubId) then
        throw IllegalArgumentException(
          s"Player ${existingPlayer.id.value} is already a member of club ${command.clubId.value}"
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
  ): AuditEvent =
    AuditEvent(
      id = AuditIdGenerator.auditEventId(),
      aggregateType = "club-application",
      aggregateId = command.clubId.value,
      eventType = "ClubApplicationSubmitted",
      occurredAt = command.submittedAt,
      actorId = command.actor.playerId,
      details = Map(
        "clubId" -> command.clubId.value,
        "membershipId" -> application.id.value,
        "displayName" -> application.displayName
      )
    )

  private def submitApplicationNotifications(
      connection: java.sql.Connection,
      command: SubmitClubApplicationCommand,
      application: ClubMembershipApplication
  ): Vector[CreateNotificationRequest] =
    val club = riichinexus.microservices.club.tables.clubs.ClubTable
      .findById(connection, command.clubId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
    val recipients = (club.admins :+ club.creator).distinct

    recipients.map { recipient =>
      CreateNotificationRequest(
        recipientPlayerId = recipient.value,
        notificationType = "ClubApplicationSubmitted",
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

  private final case class SubmitClubApplicationCommand(
      actor: AccessPrincipal,
      clubId: ClubId,
      submittedAt: Instant,
      input: ResolvedClubApplicationInput,
      message: Option[String]
  )

  private final case class ResolvedClubApplicationInput(
      playerId: PlayerId,
      displayName: String
  )
