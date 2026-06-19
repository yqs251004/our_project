package riichinexus.microservices.club.domain.membershipmanagement.model

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given
/** ClubTitleAssignment 表示后端领域中的俱乐部称号Assignment状态或规则，包含玩家 ID、标题、assignedBy、assignedAt、note。 */
final case class ClubTitleAssignment(
    playerId: PlayerId,
    title: String,
    assignedBy: PlayerId,
    assignedAt: Instant,
    note: Option[String] = None
)