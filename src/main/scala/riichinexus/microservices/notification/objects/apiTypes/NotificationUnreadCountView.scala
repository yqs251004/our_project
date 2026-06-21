package riichinexus.microservices.notification.objects.apiTypes

import upickle.default.ReadWriter

/** 通知入口徽标使用的未读数量视图。
  *
  * 它只返回聚合后的数量，避免页面为了更新徽标拉取完整通知列表。
  */
final case class NotificationUnreadCountView(
    unreadCount: Int
) derives ReadWriter
