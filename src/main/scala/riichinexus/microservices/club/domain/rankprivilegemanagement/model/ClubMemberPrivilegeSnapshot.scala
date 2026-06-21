package riichinexus.microservices.club.domain.rankprivilegemanagement.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode

import riichinexus.system.json.JsonCodecs.given

/** 根据当前贡献、等级树和管理员身份计算出的成员权限快照。
  *
  * 快照面向授权与前端展示，包含成员等级、可用俱乐部权限、管理员标记和内部称号，避免调用方重复推导权限。
  */
final case class ClubMemberPrivilegeSnapshot(
    playerId: PlayerId,
    contribution: Int,
    rankCode: String,
    rankLabel: String,
    privileges: Vector[ClubPrivilegeCode],
    isAdmin: Boolean,
    internalTitle: Option[String] = None
)
