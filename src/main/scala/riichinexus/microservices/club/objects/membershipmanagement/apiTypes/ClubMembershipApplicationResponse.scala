package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import upickle.default.ReadWriter
import riichinexus.system.json.JsonCodecs.given

/** ClubMembershipApplicationResponse 表示俱乐部成员资格申请响应 的 API 响应结果，包含 ID、玩家 ID、显示名、submittedAt、消息、状态等。 */

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
) derives ReadWriter
