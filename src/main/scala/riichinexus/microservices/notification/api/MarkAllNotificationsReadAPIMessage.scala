package riichinexus.microservices.notification.api

import cats.effect.IO
import riichinexus.microservices.notification.objects.apiTypes.MarkAllNotificationsReadResponse
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
/** 将玩家所有通知标记为已读。 */
final case class MarkAllNotificationsReadAPIMessage(
    operatorId: String
) extends APIMessage[MarkAllNotificationsReadResponse]:

  override def plan(context: ApiPlanContext): IO[MarkAllNotificationsReadResponse] =
    for
      readAt <- IO.realTimeInstant
      count <- IO.blocking {
        riichinexus.microservices.notification.tables.notifications.NotificationTable
          .markAllRead(context.connection, PlayerId(operatorId), readAt)
      }
    yield MarkAllNotificationsReadResponse(count)
