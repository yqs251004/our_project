package riichinexus.microservices.club.domain.membershipmanagement.model

import java.time.Instant

import riichinexus.domain.model.{MembershipApplicationId, PlayerId}
import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus

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
) derives CanEqual
