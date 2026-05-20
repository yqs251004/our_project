package riichinexus.microservices.club.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ClubMembershipApplicantView(
    playerId: Option[PlayerId],
    applicantUserId: Option[String],
    displayName: String,
    playerStatus: Option[PlayerStatus],
    currentRank: Option[RankSnapshot],
    elo: Option[Int],
    clubIds: Vector[ClubId]
) derives CanEqual

final case class ClubMembershipApplicationView(
    applicationId: MembershipApplicationId,
    clubId: ClubId,
    clubName: String,
    applicant: ClubMembershipApplicantView,
    submittedAt: Instant,
    message: Option[String],
    status: ClubMembershipApplicationStatus,
    reviewedBy: Option[PlayerId],
    reviewedByDisplayName: Option[String],
    reviewedAt: Option[Instant],
    reviewNote: Option[String],
    withdrawnByPrincipalId: Option[String],
    canReview: Boolean,
    canWithdraw: Boolean
) derives CanEqual

enum ClubTournamentParticipationStatus derives CanEqual:
  case Invited
  case Participating

final case class ClubTournamentParticipationView(
    clubId: ClubId,
    tournamentId: TournamentId,
    name: String,
    status: TournamentStatus,
    clubParticipationStatus: ClubTournamentParticipationStatus,
    stageName: Option[String],
    startsAt: Instant,
    endsAt: Instant,
    canViewDetail: Boolean,
    canSubmitLineup: Boolean,
    canDecline: Boolean
) derives CanEqual

type ClubMembershipApplication = riichinexus.domain.model.ClubMembershipApplication

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
  given ReadWriter[ClubTournamentParticipationStatus] =
    readwriter[String].bimap[ClubTournamentParticipationStatus](_.toString, ClubTournamentParticipationStatus.valueOf)
  given ReadWriter[ClubTournamentParticipationView] = macroRW
  given ReadWriter[ClubTournamentQuery] = macroRW
