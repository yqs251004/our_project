package riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes

import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** ClubMemberPrivilegeSnapshotView 表示俱乐部成员权限快照视图 的前端展示视图，包含玩家 ID、contribution、rankCode、rankLabel、privileges、isAdmin等。 */

final case class ClubMemberPrivilegeSnapshotView(
    playerId: String,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[ClubPrivilegeCode],
    isAdmin: Boolean,
    internalTitle: Option[String]
) derives ReadWriter
