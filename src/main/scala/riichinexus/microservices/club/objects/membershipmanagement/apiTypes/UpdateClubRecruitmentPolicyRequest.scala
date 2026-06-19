package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** UpdateClubRecruitmentPolicyRequest 表示更新俱乐部招募策略请求 的前端请求参数。 */

final case class UpdateClubRecruitmentPolicyRequest(
    operatorId: String,
    applicationsOpen: Boolean,
    requirementsText: Option[String] = None,
    expectedReviewSlaHours: Option[Int] = None,
    note: Option[String] = None
)

object UpdateClubRecruitmentPolicyRequest:
  given ReadWriter[UpdateClubRecruitmentPolicyRequest] = macroRW
