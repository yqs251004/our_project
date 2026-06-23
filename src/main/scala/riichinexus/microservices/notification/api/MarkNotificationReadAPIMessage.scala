package riichinexus.microservices.notification.api

import cats.effect.IO
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.tables.notifications.NotificationTable
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 将单条玩家通知标记为已读。 */
final case class MarkNotificationReadAPIMessage(
    notificationId: String,
    operatorId: String
) extends APIMessage[Option[Notification]]:

  override def plan(context: ApiPlanContext): IO[Option[Notification]] =
    for
      readAt <- IO.realTimeInstant
      notification <- IO.blocking {
        NotificationTable
          .markRead(context.connection, notificationId, PlayerId(operatorId), readAt)
      }
    yield notification
