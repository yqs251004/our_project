package riichinexus.microservices.notification.api

import cats.effect.IO
import riichinexus.microservices.notification.objects.apiTypes.NotificationUnreadCountView
import riichinexus.microservices.notification.tables.notifications.NotificationTable
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import upickle.default.*

final case class GetUnreadNotificationCountAPIMessage(
    operatorId: String
) extends APIMessage[NotificationUnreadCountView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[NotificationUnreadCountView] =
    IO.blocking {
      NotificationUnreadCountView(
        unreadCount = NotificationTable.countUnread(context.connection, PlayerId(operatorId))
      )
    }
