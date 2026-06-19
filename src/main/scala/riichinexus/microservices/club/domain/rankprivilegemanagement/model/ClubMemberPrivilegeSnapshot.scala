package riichinexus.microservices.club.domain.rankprivilegemanagement.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode

import riichinexus.system.json.JsonCodecs.given
/** ClubMemberPrivilegeSnapshot 表示后端领域中的俱乐部成员权限快照状态或规则，包含玩家 ID、contribution、rankCode、rankLabel、privileges、isAdmin等。 */
final case class ClubMemberPrivilegeSnapshot(
    playerId: PlayerId,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[ClubPrivilegeCode],
    isAdmin: Boolean,
    internalTitle: Option[String] = None
)