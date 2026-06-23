package riichinexus.microservices.club.domain.membership.model

import riichinexus.system.json.JsonCodecs.given

/** 俱乐部对外招募的公开策略。
  *
  * 策略决定是否开放申请、申请页面展示的要求说明，以及管理员预期在多少小时内完成审核。
  */
final case class ClubRecruitmentPolicy(
    applicationsOpen: Boolean = true,
    requirementsText: Option[String] = Some("Open to guest or registered applicants; final approval is handled manually by club admins."),
    expectedReviewSlaHours: Option[Int] = Some(72)
)
