package riichinexus.microservices.notification.router

import riichinexus.microservices.notification.api.*
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.apiTypes.*
import riichinexus.system.api.RegisteredAPIMessage
import riichinexus.system.json.JsonCodecs.given

object NotificationAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[ListNotificationsAPIMessage, Vector[Notification]],
      RegisteredAPIMessage.api[GetUnreadNotificationCountAPIMessage, NotificationUnreadCountView],
      RegisteredAPIMessage.api[MarkNotificationReadAPIMessage, Option[Notification]],
      RegisteredAPIMessage.api[MarkAllNotificationsReadAPIMessage, MarkAllNotificationsReadResponse]
    )
