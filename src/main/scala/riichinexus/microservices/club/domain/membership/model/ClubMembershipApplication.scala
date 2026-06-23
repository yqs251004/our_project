package riichinexus.microservices.club.domain.membership.model

import java.time.Instant

import riichinexus.microservices.club.objects.membership.ClubApplicationStatus
import riichinexus.microservices.club.objects.membership.MembershipApplicationId
import riichinexus.microservices.player.objects.PlayerId

/** 玩家或游客提交的俱乐部入会申请。
  *
  * 申请可以来自已绑定玩家或仅有访客账号的申请人，并记录展示名、申请留言、审核状态、审核人、审核备注和撤回主体。
  */
final case class ClubMembershipApplication(
    id: MembershipApplicationId,
    playerId: Option[PlayerId] = None,
    applicantUserId: Option[String] = None,
    displayName: String,
    submittedAt: Instant,
    message: Option[String] = None,
    status: ClubApplicationStatus = ClubApplicationStatus.Pending,
    reviewedBy: Option[PlayerId] = None,
    reviewedAt: Option[Instant] = None,
    reviewNote: Option[String] = None,
    withdrawnByPrincipalId: Option[String] = None
)
