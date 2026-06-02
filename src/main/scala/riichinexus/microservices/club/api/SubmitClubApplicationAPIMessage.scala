package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

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
      actor <- ResolveRequestActor(request.guestSessionId.map(GuestSessionId(_)), request.operatorId.map(PlayerId(_))).plan(context)
      parsedClubId = ClubId(clubId)
      submittedAt <- IO.realTimeInstant
      resolvedInput <- IO.blocking(resolveApplicantInput(context.connection, actor, request))
      command = SubmitClubApplicationCommand(
        actor = actor,
        clubId = parsedClubId,
        submittedAt = submittedAt,
        input = resolvedInput,
        message = request.message
      )
      application <- IO.blocking {
        {
          submitApplication(context.connection, command)
        }
      }
    yield ClubMembershipApplicationResponse.fromDomain(application)

  private def resolveApplicantInput(
      connection: java.sql.Connection,
      actor: AccessPrincipal,
      request: ClubMembershipApplicationRequest
  ): ResolvedClubApplicationInput =
    val operatorPlayer = request.operatorId.filter(_.nonEmpty)
      .flatMap(id => PlayerPersistenceFunctions.findPlayer(connection, PlayerId(id)))
    val applicantUserId = request.applicantUserId
      .orElse(request.guestSessionId.filter(_.nonEmpty).map(session => s"guest:$session"))
      .orElse(operatorPlayer.map(_.userId))
    val displayName = request.guestSessionId.filter(_.nonEmpty).map(_ => actor.displayName)
      .orElse(operatorPlayer.map(_.nickname))
      .getOrElse(request.displayName)
    ResolvedClubApplicationInput(
      applicantUserId = applicantUserId,
      displayName = displayName
    )

  private def submitApplication(
      connection: java.sql.Connection,
      command: SubmitClubApplicationCommand
  ): ClubMembershipApplication =
    AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, command.actor, Permission.SubmitClubApplication)
    val club = riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
    validateSubmission(connection, club, command)
    val application = createApplication(command)
    riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.submitApplication(club, application))
    application

  private def validateSubmission(
      connection: java.sql.Connection,
      club: Club,
      command: SubmitClubApplicationCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ensureApplicationsOpen(club, command.clubId)
    ensureDisplayNameNonEmpty(command.input.displayName)
    ensureNoPendingApplication(club, command)
    ensureApplicantNotAlreadyMember(connection, command)

  private def ensureApplicationsOpen(club: Club, clubId: ClubId): Unit =
    if !club.recruitmentPolicy.applicationsOpen then
      throw IllegalArgumentException(s"Club ${clubId.value} is not currently accepting membership applications")

  private def ensureDisplayNameNonEmpty(displayName: String): Unit =
    if displayName.trim.isEmpty then
      throw IllegalArgumentException("Membership application display name cannot be empty")

  private def ensureNoPendingApplication(club: Club, command: SubmitClubApplicationCommand): Unit =
    command.input.applicantUserId.foreach { userId =>
      if club.membershipApplications.exists(application =>
          application.applicantUserId.contains(userId) && ClubMembershipApplicationFunctions.isPending(application)
        )
      then
        throw IllegalArgumentException(
          s"User $userId already has a pending application for club ${command.clubId.value}"
        )
    }

  private def ensureApplicantNotAlreadyMember(
      connection: java.sql.Connection,
      command: SubmitClubApplicationCommand
  ): Unit =
    command.input.applicantUserId.foreach { userId =>
      PlayerPersistenceFunctions.findPlayerByUserId(connection, userId).foreach { existingPlayer =>
        if PlayerClubBindingFunctions.boundClubIds(existingPlayer).contains(command.clubId) then
          throw IllegalArgumentException(
            s"Player ${existingPlayer.id.value} is already a member of club ${command.clubId.value}"
          )
      }
    }

  private def createApplication(command: SubmitClubApplicationCommand): ClubMembershipApplication =
    ClubMembershipApplication(
      id = ClubIdGenerator.membershipApplicationId(),
      applicantUserId = command.input.applicantUserId,
      displayName = command.input.displayName,
      submittedAt = command.submittedAt,
      message = command.message
    )

  private final case class SubmitClubApplicationCommand(
      actor: AccessPrincipal,
      clubId: ClubId,
      submittedAt: Instant,
      input: ResolvedClubApplicationInput,
      message: Option[String]
  )

  private final case class ResolvedClubApplicationInput(
      applicantUserId: Option[String],
      displayName: String
  )
