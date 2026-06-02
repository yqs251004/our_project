package riichinexus.microservices.notification.api

import cats.effect.IO
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.tables.notifications.NotificationTable
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class MarkNotificationReadAPIMessage(
    notificationId: String,
    operatorId: String
) extends APIMessage[Option[Notification]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[Notification]] =
    for
      readAt <- IO.realTimeInstant
      notification <- IO.blocking {
        NotificationTable
          .markRead(context.connection, notificationId, PlayerId(operatorId), readAt)
      }
    yield notification
