package riichinexus.microservices.club.objects.apiTypes

import upickle.default.*

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
