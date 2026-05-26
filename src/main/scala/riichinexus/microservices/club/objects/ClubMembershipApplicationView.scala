package riichinexus.microservices.club.objects

import upickle.default.*

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
) derives ReadWriter
