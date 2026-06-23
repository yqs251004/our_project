package riichinexus.microservices.club.objects.rankprivilege

import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 某位成员在俱乐部内的当前等级和权限快照。
  *
  * 视图同时表达贡献值推导出的等级、等级权限、管理员身份和内部称号，供成员管理页判断可操作入口。
  */
final case class ClubMemberPrivilegeSnapshotView(
    playerId: String,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[ClubPrivilegeCode],
    isAdmin: Boolean,
    internalTitle: Option[String]
)

object ClubMemberPrivilegeSnapshotView:
  given ReadWriter[ClubMemberPrivilegeSnapshotView] = macroRW
