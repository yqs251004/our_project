package riichinexus.microservices.club.domain.model

import java.time.Instant

import riichinexus.domain.model.{MembershipApplicationId, PlayerId}
import riichinexus.microservices.club.objects.ClubApplicationStatus

final case class ClubMembershipApplication(
    id: MembershipApplicationId,
    applicantUserId: Option[String],
    displayName: String,
    submittedAt: Instant,
    message: Option[String] = None,
    status: ClubApplicationStatus = ClubApplicationStatus.Pending,
    reviewedBy: Option[PlayerId] = None,
    reviewedAt: Option[Instant] = None,
    reviewNote: Option[String] = None,
    withdrawnByPrincipalId: Option[String] = None
) derives CanEqual:
  def isPending: Boolean =
    status == ClubApplicationStatus.Pending

  def approve(by: PlayerId, at: Instant, note: Option[String] = None): ClubMembershipApplication =
    require(isPending, "Only pending applications can be approved")
    copy(
      status = ClubApplicationStatus.Approved,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def reject(by: PlayerId, at: Instant, note: Option[String] = None): ClubMembershipApplication =
    require(isPending, "Only pending applications can be rejected")
    copy(
      status = ClubApplicationStatus.Rejected,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def withdraw(
      byPrincipalId: String,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending, "Only pending applications can be withdrawn")
    copy(
      status = ClubApplicationStatus.Withdrawn,
      reviewedAt = Some(at),
      reviewNote = note,
      withdrawnByPrincipalId = Some(byPrincipalId)
    )

  def bindRegisteredApplicant(
      userId: String,
      updatedDisplayName: String
  ): ClubMembershipApplication =
    require(isPending, "Only pending applications can be rebound to a registered applicant")
    require(userId.trim.nonEmpty, "Bound applicant userId cannot be empty")
    require(updatedDisplayName.trim.nonEmpty, "Bound applicant display name cannot be empty")
    copy(
      applicantUserId = Some(userId.trim),
      displayName = updatedDisplayName.trim
    )
