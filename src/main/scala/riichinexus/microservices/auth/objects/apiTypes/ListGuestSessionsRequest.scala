package riichinexus.microservices.auth.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 后台查询游客会话列表的过滤和分页参数。
  *
  * `activeOnly` 用于只看仍可用的游客会话，`limit` 与 `offset` 让管理页按批次浏览历史记录。
  */
final case class ListGuestSessionsRequest(
    activeOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object ListGuestSessionsRequest:
  given ReadWriter[ListGuestSessionsRequest] = macroRW
