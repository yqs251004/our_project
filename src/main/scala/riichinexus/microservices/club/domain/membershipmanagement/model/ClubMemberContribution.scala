package riichinexus.microservices.club.domain.membershipmanagement.model

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given
/** ClubMemberContribution 表示后端领域中的俱乐部成员贡献状态或规则，包含玩家 ID、amount、更新时间、updatedBy、note。 */
final case class ClubMemberContribution(
    playerId: PlayerId,
    amount: Int,
    updatedAt: Instant,
    updatedBy: PlayerId,
    note: Option[String] = None
)