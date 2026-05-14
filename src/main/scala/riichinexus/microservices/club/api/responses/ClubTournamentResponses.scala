package riichinexus.microservices.club.api.responses

import java.time.Instant

import riichinexus.domain.model.*

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

type ClubMembershipApplicationResponse = ClubMembershipApplicationView
type ClubTournamentParticipationResponse = ClubTournamentParticipationView

object ClubTournamentResponses:
  export ClubResponseCodecs.given
