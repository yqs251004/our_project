package riichinexus.microservices.club.domain.membershipmanagement.model

import riichinexus.system.json.JsonCodecs.given
/** ClubRecruitmentPolicy 表示后端领域中的俱乐部招募策略状态或规则，包含applicationsOpen、requirementsText、expectedReviewSlaHours。 */
final case class ClubRecruitmentPolicy(
    applicationsOpen: Boolean = true,
    requirementsText: Option[String] = Some("Open to guest or registered applicants; final approval is handled manually by club admins."),
    expectedReviewSlaHours: Option[Int] = Some(72)
)