package riichinexus.microservices.notification.objects.apiTypes

import upickle.default.*

final case class NotificationUnreadCountView(
    unreadCount: Int
) derives ReadWriter
