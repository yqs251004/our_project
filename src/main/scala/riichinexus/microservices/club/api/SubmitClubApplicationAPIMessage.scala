package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.apiTypes.{ClubMembershipApplicationResponse, ClubMembershipApplicationRequest}
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class SubmitClubApplicationAPIMessage(
    clubId: String,
    request: ClubMembershipApplicationRequest
) extends APIMessage[ClubMembershipApplicationResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationResponse] =
    for
      actor <- IO(context.requestActor(request.session, request.operator))
      module = context.support.clubModule
      parsedClubId = ClubId(clubId)
      submittedAt <- IO.realTimeInstant
      resolvedInput <- IO(resolveApplicantInput(context.connection, module, actor, request))
      command = SubmitClubApplicationCommand(
        actor = actor,
        clubId = parsedClubId,
        submittedAt = submittedAt,
        input = resolvedInput,
        message = request.message
      )
      application <- IO {
        module.transactionManager.inTransaction {
          submitApplication(context.connection, module, command)
        }
      }
    yield ClubMembershipApplicationResponse.fromDomain(application)

  private def resolveApplicantInput(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      actor: AccessPrincipal,
      request: ClubMembershipApplicationRequest
  ): ResolvedClubApplicationInput =
    val operatorPlayer = request.operatorId.filter(_.nonEmpty)
      .flatMap(id => PlayerTable.findById(connection, PlayerId(id)))
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
      module: ClubModuleContext,
      command: SubmitClubApplicationCommand
  ): ClubMembershipApplication =
    module.authorizationService.requirePermission(command.actor, Permission.SubmitClubApplication)
    val club = riichinexus.microservices.club.tables.club.ClubTable.findById(connection, command.clubId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
    validateSubmission(connection, module, club, command)
    val application = createApplication(command)
    riichinexus.microservices.club.tables.club.ClubTable.save(connection, club.submitApplication(application))
    application

  private def validateSubmission(
      connection: java.sql.Connection,
      module: ClubModuleContext,
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
          application.applicantUserId.contains(userId) && application.isPending
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
      PlayerTable.findByUserId(connection, userId).foreach { existingPlayer =>
        if existingPlayer.boundClubIds.contains(command.clubId) then
          throw IllegalArgumentException(
            s"Player ${existingPlayer.id.value} is already a member of club ${command.clubId.value}"
          )
      }
    }

  private def createApplication(command: SubmitClubApplicationCommand): ClubMembershipApplication =
    ClubMembershipApplication(
      id = IdGenerator.membershipApplicationId(),
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
