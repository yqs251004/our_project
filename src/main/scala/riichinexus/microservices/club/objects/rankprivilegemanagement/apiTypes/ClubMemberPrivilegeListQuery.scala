package riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes

import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 查询俱乐部成员权限快照列表的过滤参数。
  *
  * 可按成员、权限编码或等级筛选，帮助管理员快速找出拥有某类能力或处于某个等级的成员。
  */
final case class ClubMemberPrivilegeListQuery(
    playerId: Option[String] = None,
    privilege: Option[ClubPrivilegeCode] = None,
    rankCode: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubMemberPrivilegeListQuery:
  given ReadWriter[ClubMemberPrivilegeListQuery] = macroRW
