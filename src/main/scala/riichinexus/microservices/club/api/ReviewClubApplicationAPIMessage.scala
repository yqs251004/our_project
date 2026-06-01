package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
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
      actor <- IO.blocking(AuthAccessPrincipalResolver.principal(context, PlayerId(request.operatorId)))
      reviewedAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = ReviewClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        actor = actor,
        requestedPlayerId = request.playerId.map(PlayerId(_)),
        decision = decision,
        note = request.note,
        reviewedAt = reviewedAt
      )
      result <- IO.blocking(reviewApplication(context.connection, module, command))
    yield ClubApplicationViewAssembler.applicationView(
      context.connection,
      module,
      result.club,
      result.application,
      command.actor
    )

  private def reviewApplication(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: ReviewClubApplicationCommand
  ): ReviewClubApplicationResult =
    val reviewedClub = submitReview(connection, module, command)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    val reviewedApplication = ClubFunctions.findApplication(reviewedClub, command.membershipId).getOrElse(
      throw NoSuchElementException(
        s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
      )
    )
    ReviewClubApplicationResult(reviewedClub, reviewedApplication)

  private def submitReview(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: ReviewClubApplicationCommand
  ): Option[Club] =
    command.decision match
      case ApplicationReviewDecision.Approve =>
        val player = resolveApprovedPlayer(connection, module, command)
        ClubApplicationReviewer.approve(
          connection = connection,
          module = module,
          parsedClubId = command.clubId,
          parsedMembershipId = command.membershipId,
          parsedPlayerId = player.id,
          actor = command.actor,
          note = command.note,
          approvedAt = command.reviewedAt
        )
      case ApplicationReviewDecision.Reject =>
        ClubApplicationReviewer.reject(
          connection = connection,
          module = module,
          parsedClubId = command.clubId,
          parsedMembershipId = command.membershipId,
          actor = command.actor,
          note = command.note,
          rejectedAt = command.reviewedAt
        )

  private def resolveApprovedPlayer(
      connection: java.sql.Connection,
      module: ClubModuleContext,
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
    command.requestedPlayerId
      .flatMap(GetPlayerAPIMessage.findPlayer(connection, _))
      .orElse(
        application.applicantUserId
          .filterNot(_.startsWith("guest:"))
          .flatMap(CreatePlayerAPIMessage.findPlayerByUserId(connection, _))
      )
      .getOrElse(
        throw IllegalArgumentException(
          s"Membership application ${command.membershipId.value} requires playerId when approving a guest-origin application"
        )
      )

  private def resolveDecision(decision: ClubApplicationReviewDecision): ApplicationReviewDecision =
    decision match
      case ClubApplicationReviewDecision.Approve => ApplicationReviewDecision.Approve
      case ClubApplicationReviewDecision.Reject  => ApplicationReviewDecision.Reject

  private enum ApplicationReviewDecision:
    case Approve, Reject

  private final case class ReviewClubApplicationCommand(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      actor: AccessPrincipal,
      requestedPlayerId: Option[PlayerId],
      decision: ApplicationReviewDecision,
      note: Option[String],
      reviewedAt: Instant
  )

  private final case class ReviewClubApplicationResult(
      club: Club,
      application: ClubMembershipApplication
  )
