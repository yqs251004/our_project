package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.RankSnapshotView
import upickle.default.*

final case class ClubMembershipApplicantView(
    playerId: Option[String],
    applicantUserId: Option[String],
    displayName: String,
    playerStatus: Option[String],
    currentRank: Option[RankSnapshotView],
    elo: Option[Int],
    clubIds: Vector[String]
) derives CanEqual

final case class ClubMembershipApplicationView(
    applicationId: String,
    clubId: String,
    clubName: String,
    applicant: ClubMembershipApplicantView,
    submittedAt: String,
    message: Option[String],
    status: String,
    reviewedBy: Option[String],
    reviewedByDisplayName: Option[String],
    reviewedAt: Option[String],
    reviewNote: Option[String],
    withdrawnByPrincipalId: Option[String],
    canReview: Boolean,
    canWithdraw: Boolean
) derives CanEqual

enum ClubTournamentParticipationStatus derives CanEqual:
  case Invited
  case Participating

final case class ClubTournamentParticipationView(
    clubId: String,
    tournamentId: String,
    name: String,
    status: String,
    clubParticipationStatus: String,
    stageName: Option[String],
    startsAt: String,
    endsAt: String,
    canViewDetail: Boolean,
    canSubmitLineup: Boolean,
    canDecline: Boolean
) derives CanEqual

final case class ClubMembershipApplication(
    id: String,
    applicantUserId: Option[String],
    displayName: String,
    submittedAt: String,
    message: Option[String],
    status: String,
    reviewedBy: Option[String],
    reviewedAt: Option[String],
    reviewNote: Option[String],
    withdrawnByPrincipalId: Option[String]
) derives ReadWriter

object ClubMembershipApplication:
  def fromDomain(application: riichinexus.domain.model.ClubMembershipApplication): ClubMembershipApplication =
    ClubMembershipApplication(
      id = application.id.value,
      applicantUserId = application.applicantUserId,
      displayName = application.displayName,
      submittedAt = application.submittedAt.toString,
      message = application.message,
      status = application.status.toString,
      reviewedBy = application.reviewedBy.map(_.value),
      reviewedAt = application.reviewedAt.map(_.toString),
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId
    )

final case class ClubTournamentQuery(
    scope: Option[String] = None,
    viewer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

type ClubMembershipApplicationResponse = ClubMembershipApplicationView
type ClubTournamentParticipationResponse = ClubTournamentParticipationView

object ClubTournamentResponses:
  given ReadWriter[ClubMembershipApplicantView] = macroRW
  given ReadWriter[ClubMembershipApplicationView] = macroRW
  given ReadWriter[ClubTournamentParticipationView] = macroRW
  given ReadWriter[ClubMembershipApplication] = macroRW
  given ReadWriter[ClubTournamentQuery] = macroRW
