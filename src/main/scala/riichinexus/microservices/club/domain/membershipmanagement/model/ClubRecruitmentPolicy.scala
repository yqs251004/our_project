package riichinexus.microservices.club.domain.membershipmanagement.model

final case class ClubRecruitmentPolicy(
    applicationsOpen: Boolean = true,
    requirementsText: Option[String] = Some("Open to guest or registered applicants; final approval is handled manually by club admins."),
    expectedReviewSlaHours: Option[Int] = Some(72)
)
