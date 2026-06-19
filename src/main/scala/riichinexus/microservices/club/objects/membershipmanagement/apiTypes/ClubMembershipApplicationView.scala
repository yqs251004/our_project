package riichinexus.microservices.club.objects.membershipmanagement.apiTypes

import upickle.default.ReadWriter
import riichinexus.system.json.JsonCodecs.given

/** ClubMembershipApplicationView 表示俱乐部成员资格申请视图 的前端展示视图，包含applicationId、俱乐部 ID、clubName、applicant、submittedAt、消息等。 */

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
