package riichinexus.microservices.notification.api

import cats.effect.IO
import riichinexus.microservices.notification.objects.NotificationUnreadCountView
import riichinexus.microservices.notification.tables.notifications.NotificationTable
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
/** 获取玩家未读通知数量。 */
final case class GetUnreadNotificationCountAPIMessage(
    operatorId: String
) extends APIMessage[NotificationUnreadCountView]:

  override def plan(context: ApiPlanContext): IO[NotificationUnreadCountView] =
    IO.blocking {
      NotificationUnreadCountView(
        unreadCount = NotificationTable.countUnread(context.connection, PlayerId(operatorId))
      )
    }
