package riichinexus.microservices.club.domain.membershipmanagement.model

import java.time.Instant

import riichinexus.microservices.club.objects.membershipmanagement.ClubApplicationStatus
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.player.objects.playerprofile.PlayerId

/** ClubMembershipApplication 表示后端领域中的俱乐部成员资格申请状态或规则，包含 ID、玩家 ID、applicantUserId、显示名、submittedAt、消息等。 */

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
