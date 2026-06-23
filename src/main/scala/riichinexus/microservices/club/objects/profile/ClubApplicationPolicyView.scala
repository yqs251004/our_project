package riichinexus.microservices.club.objects.profile

import upickle.default.{ReadWriter, macroRW}
import riichinexus.system.json.JsonCodecs.given

/** 公开给申请人的俱乐部入会策略摘要。
  *
  * 它说明是否开放申请、申请要求、预计审核时长和当前积压数量，帮助玩家判断是否现在提交申请。
  */
final case class ClubApplicationPolicyView(
    applicationsOpen: Boolean,
    requirementsText: Option[String],
    expectedReviewSlaHours: Option[Int],
    pendingApplicationCount: Int
)

object ClubApplicationPolicyView:
  given ReadWriter[ClubApplicationPolicyView] = macroRW
