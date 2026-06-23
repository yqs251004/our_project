package riichinexus.microservices.notification.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 前端通知中心读取通知列表时使用的过滤和分页参数。
  *
  * `unreadOnly` 支持只看待处理消息，`limit` 和 `offset` 支持滚动加载历史通知。
  */
final case class NotificationListQuery(
    unreadOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object NotificationListQuery:
  given ReadWriter[NotificationListQuery] = macroRW
