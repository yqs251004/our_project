package riichinexus.microservices.club.objects.tournamentparticipation.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.{ReadWriter, macroRW}

/** 公开俱乐部阵容中单个成员的展示资料。
  *
  * 视图包含昵称、Elo、段位、成员状态、管理员标记、内部称号和权限标签，方便页面展示当前可派出的核心成员。
  */
final case class PublicClubLineupMemberView(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshot,
    status: String,
    isAdmin: Boolean,
    internalTitle: Option[String],
    privileges: Vector[ClubPrivilegeCode]
)

object PublicClubLineupMemberView:
  given ReadWriter[PublicClubLineupMemberView] = macroRW
