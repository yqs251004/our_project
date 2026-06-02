package riichinexus.microservices.notification.api

import cats.effect.IO
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import upickle.default.*

final case class MarkAllNotificationsReadResponse(
    updatedCount: Int
) derives ReadWriter

final case class MarkAllNotificationsReadAPIMessage(
    operatorId: String
) extends APIMessage[MarkAllNotificationsReadResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[MarkAllNotificationsReadResponse] =
    for
      readAt <- IO.realTimeInstant
      count <- IO.blocking {
        riichinexus.microservices.notification.tables.notifications.NotificationTable
          .markAllRead(context.connection, PlayerId(operatorId), readAt)
      }
    yield MarkAllNotificationsReadResponse(count)
