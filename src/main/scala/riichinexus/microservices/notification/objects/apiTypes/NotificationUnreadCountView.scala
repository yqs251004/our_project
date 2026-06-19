package riichinexus.microservices.notification.objects.apiTypes

import upickle.default.ReadWriter

/** NotificationUnreadCountView 表示通知未读数量视图 的前端展示视图，包含unreadCount。 */

final case class NotificationUnreadCountView(
    unreadCount: Int
) derives ReadWriter
