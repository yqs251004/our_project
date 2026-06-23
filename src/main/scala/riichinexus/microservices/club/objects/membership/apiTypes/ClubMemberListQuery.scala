package riichinexus.microservices.club.objects.membership.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 查询俱乐部成员列表时使用的筛选和分页参数。
  *
  * 成员状态和昵称用于页面检索，返回字段范围仍由后端根据当前访问权限决定。
  */
final case class ClubMemberListQuery(
    status: Option[String] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubMemberListQuery:
  given ReadWriter[ClubMemberListQuery] = macroRW
