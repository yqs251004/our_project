package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.domain.membershipmanagement.model.ClubRecruitmentPolicy
import upickle.default.*

final case class UpdateClubRecruitmentPolicyRequest(
    operatorId: String,
    applicationsOpen: Boolean,
    requirementsText: Option[String] = None,
    expectedReviewSlaHours: Option[Int] = None,
    note: Option[String] = None
)

object UpdateClubRecruitmentPolicyRequest:
  given ReadWriter[UpdateClubRecruitmentPolicyRequest] = macroRW
