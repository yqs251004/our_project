package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 管理侧查询俱乐部列表的过滤和分页参数。
  *
  * 该查询支持按活跃状态、可加入状态、成员、管理员和名称筛选，服务于后台或成员中心的俱乐部检索。
  */
final case class ClubListQuery(
    activeOnly: Option[Boolean] = None,
    joinableOnly: Option[Boolean] = None,
    memberId: Option[String] = None,
    adminId: Option[String] = None,
    name: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ClubListQuery:
  given ReadWriter[ClubListQuery] = macroRW
