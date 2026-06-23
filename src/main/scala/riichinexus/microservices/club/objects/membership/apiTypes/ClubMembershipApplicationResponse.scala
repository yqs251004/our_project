package riichinexus.microservices.club.objects.membership.apiTypes

import upickle.default.{ReadWriter, macroRW}
import riichinexus.system.json.JsonCodecs.given

/** 提交、撤回或审核入会申请后返回的申请状态快照。
  *
  * 该响应保留申请人、提交时间、审核结果、审核备注和撤回主体，让调用端可以在动作完成后立即刷新申请卡片。
  */
final case class ClubMembershipApplicationResponse(
    id: String,
    playerId: Option[String],
    displayName: String,
    submittedAt: String,
    message: Option[String],
    status: String,
    reviewedBy: Option[String],
    reviewedAt: Option[String],
    reviewNote: Option[String],
    withdrawnByPrincipalId: Option[String]
)

object ClubMembershipApplicationResponse:
  given ReadWriter[ClubMembershipApplicationResponse] = macroRW
