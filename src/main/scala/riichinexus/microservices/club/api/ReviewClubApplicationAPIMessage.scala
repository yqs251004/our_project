package riichinexus.microservices.club.api
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

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
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
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
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(request.operatorId)).resolve(context.connection))
      reviewedAt <- IO.realTimeInstant
      command = ReviewClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        actor = actor,
        requestedPlayerId = request.playerId.map(PlayerId(_)),
        decision = decision,
        note = request.note,
        reviewedAt = reviewedAt
      )
      result <- IO.blocking(reviewApplication(context.connection, command))
    yield ClubApplicationViewAssembler.applicationView(
      context.connection,
      result.club,
      result.application,
      command.actor
    )

  private def reviewApplication(
      connection: java.sql.Connection,
      command: ReviewClubApplicationCommand
  ): ReviewClubApplicationResult =
    val reviewedClub = submitReview(connection, command)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    val reviewedApplication = ClubFunctions.findApplication(reviewedClub, command.membershipId).getOrElse(
      throw NoSuchElementException(
        s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
      )
    )
    ReviewClubApplicationResult(reviewedClub, reviewedApplication)

  private def submitReview(
      connection: java.sql.Connection,
      command: ReviewClubApplicationCommand
  ): Option[Club] =
    command.decision match
      case ApplicationReviewDecision.Approve =>
        val player = resolveApprovedPlayer(connection, command)
        ClubApplicationReviewer.approve(
          connection = connection,          parsedClubId = command.clubId,
          parsedMembershipId = command.membershipId,
          parsedPlayerId = player.id,
          actor = command.actor,
          note = command.note,
          approvedAt = command.reviewedAt
        )
      case ApplicationReviewDecision.Reject =>
        ClubApplicationReviewer.reject(
          connection = connection,          parsedClubId = command.clubId,
          parsedMembershipId = command.membershipId,
          actor = command.actor,
          note = command.note,
          rejectedAt = command.reviewedAt
        )

  private def resolveApprovedPlayer(
      connection: java.sql.Connection,
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
      .flatMap(PlayerPersistenceFunctions.findPlayer(connection, _))
      .orElse(
        application.applicantUserId
          .filterNot(_.startsWith("guest:"))
          .flatMap(PlayerPersistenceFunctions.findPlayerByUserId(connection, _))
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
