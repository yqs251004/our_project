package riichinexus.microservices.club.objects.membership.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 更新俱乐部公开招募策略的管理请求。
  *
  * 它控制是否开放申请、申请要求和预计审核时长；备注与操作者用于解释这次策略变更。
  */
final case class UpdateClubRecruitmentPolicyRequest(
    operatorId: String,
    applicationsOpen: Boolean,
    requirementsText: Option[String] = None,
    expectedReviewSlaHours: Option[Int] = None,
    note: Option[String] = None
)

object UpdateClubRecruitmentPolicyRequest:
  given ReadWriter[UpdateClubRecruitmentPolicyRequest] = macroRW
