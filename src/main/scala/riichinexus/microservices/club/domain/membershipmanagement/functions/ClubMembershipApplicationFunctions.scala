package riichinexus.microservices.club.domain.membershipmanagement.functions

import java.time.Instant

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubMembershipApplication
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus

object ClubMembershipApplicationFunctions:
  def isPending(application: ClubMembershipApplication): Boolean =
    application.status == ClubApplicationStatus.Pending

  def approve(
      application: ClubMembershipApplication,
      by: PlayerId,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be approved")
    application.copy(
      status = ClubApplicationStatus.Approved,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def reject(
      application: ClubMembershipApplication,
      by: PlayerId,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be rejected")
    application.copy(
      status = ClubApplicationStatus.Rejected,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def withdraw(
      application: ClubMembershipApplication,
      byPrincipalId: String,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be withdrawn")
    application.copy(
      status = ClubApplicationStatus.Withdrawn,
      reviewedAt = Some(at),
      reviewNote = note,
      withdrawnByPrincipalId = Some(byPrincipalId)
    )

  def bindRegisteredApplicant(
      application: ClubMembershipApplication,
      userId: String,
      updatedDisplayName: String
  ): ClubMembershipApplication =
    require(isPending(application), "Only pending applications can be rebound to a registered applicant")
    require(userId.trim.nonEmpty, "Bound applicant userId cannot be empty")
    require(updatedDisplayName.trim.nonEmpty, "Bound applicant display name cannot be empty")
    application.copy(
      applicantUserId = Some(userId.trim),
      displayName = updatedDisplayName.trim
    )
