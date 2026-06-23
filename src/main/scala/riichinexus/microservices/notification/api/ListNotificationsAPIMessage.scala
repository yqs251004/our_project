package riichinexus.microservices.notification.api

import cats.effect.IO
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.apiTypes.NotificationListQuery
import riichinexus.microservices.notification.tables.notifications.NotificationTable
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 列出玩家通知。 */
final case class ListNotificationsAPIMessage(
    operatorId: String,
    query: NotificationListQuery = NotificationListQuery()
) extends APIMessage[Vector[Notification]]:

  override def plan(context: ApiPlanContext): IO[Vector[Notification]] =
    IO.blocking {
      NotificationTable
        .listForRecipient(
          context.connection,
          PlayerId(operatorId),
          query.unreadOnly.getOrElse(false),
          query.limit.getOrElse(30),
          query.offset.getOrElse(0)
        )
    }
