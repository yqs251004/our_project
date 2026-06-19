package riichinexus.microservices.notification.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** NotificationListQuery 表示通知列表查询 的列表或详情查询条件，包含仅未读、数量限制、分页偏移。 */

final case class NotificationListQuery(
    unreadOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
