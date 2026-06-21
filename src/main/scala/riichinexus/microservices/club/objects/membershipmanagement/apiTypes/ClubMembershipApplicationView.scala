package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import upickle.default.ReadWriter
import riichinexus.system.json.JsonCodecs.given

/** 入会申请列表和详情页使用的展示视图。
  *
  * 它把俱乐部、申请人、审核信息和当前用户可执行动作合并成一个模型，方便成员中心和俱乐部收件箱共用。
  */
final case class ClubMembershipApplicationView(
    applicationId: String,
    clubId: String,
    clubName: String,
    applicant: ClubMembershipApplicantView,
    submittedAt: String,
    message: Option[String],
    status: String,
    reviewedBy: Option[String],
    reviewedByDisplayName: Option[String],
    reviewedAt: Option[String],
    reviewNote: Option[String],
    withdrawnByPrincipalId: Option[String],
    canReview: Boolean,
    canWithdraw: Boolean
) derives ReadWriter
