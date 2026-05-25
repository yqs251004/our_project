package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.{ClubApplicationReviewer, ClubApplicationViewAssembler}
import riichinexus.microservices.club.objects.apiTypes.{Club as _, ClubRelation as _, ClubMembershipApplication as _, ClubPrivilegeDefinition as _, ClubMemberPrivilegeSnapshot as _, *}
import upickle.default.*

final case class ReviewClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    request: ReviewClubApplicationRequest
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      decision <- IO(resolveDecision(request.decision))
      actor <- IO(context.support.principal(request.operator))
      reviewedAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = ReviewClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        actor = actor,
        requestedPlayerId = request.player,
        decision = decision,
        note = request.note,
        reviewedAt = reviewedAt
      )
      result <- IO(reviewApplication(module, command))
    yield ClubApplicationViewAssembler.applicationView(
      module,
      result.club,
      result.application,
      command.actor
    )

  private def reviewApplication(
      module: ClubModuleContext,
      command: ReviewClubApplicationCommand
  ): ReviewClubApplicationResult =
    val reviewedClub = submitReview(module, command)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    val reviewedApplication = reviewedClub.findApplication(command.membershipId).getOrElse(
      throw NoSuchElementException(
        s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
      )
    )
    ReviewClubApplicationResult(reviewedClub, reviewedApplication)

  private def submitReview(
      module: ClubModuleContext,
      command: ReviewClubApplicationCommand
  ): Option[Club] =
    command.decision match
      case ApplicationReviewDecision.Approve =>
        val player = resolveApprovedPlayer(module, command)
        ClubApplicationReviewer.approve(
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
          module = module,
          parsedClubId = command.clubId,
          parsedMembershipId = command.membershipId,
          actor = command.actor,
          note = command.note,
          rejectedAt = command.reviewedAt
        )

  private def resolveApprovedPlayer(
      module: ClubModuleContext,
      command: ReviewClubApplicationCommand
  ): Player =
    val club = module.tables
      .findClub(command.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    val application = club.findApplication(command.membershipId).getOrElse(
      throw NoSuchElementException(
        s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
      )
    )
    command.requestedPlayerId
      .flatMap(module.tables.findPlayer)
      .orElse(
        application.applicantUserId
          .filterNot(_.startsWith("guest:"))
          .flatMap(module.tables.findPlayerByUserId)
      )
      .getOrElse(
        throw IllegalArgumentException(
          s"Membership application ${command.membershipId.value} requires playerId when approving a guest-origin application"
        )
      )

  private def resolveDecision(rawDecision: String): ApplicationReviewDecision =
    rawDecision.trim.toLowerCase match
      case "approve" | "approved" => ApplicationReviewDecision.Approve
      case "reject" | "rejected" => ApplicationReviewDecision.Reject
      case other =>
        throw IllegalArgumentException(
          s"Unsupported review decision '$other'. Supported decisions: approve, reject"
        )

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
