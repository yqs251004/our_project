package riichinexus.microservices.notification.objects.apiTypes

import upickle.default.ReadWriter

/** MarkAllNotificationsReadResponse 表示标记全部Notifications已读响应 的 API 响应结果，包含更新数量。 */

final case class MarkAllNotificationsReadResponse(
    updatedCount: Int
) derives ReadWriter
