package riichinexus.microservices.notification.api.`private`

import cats.effect.IO
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.system.api.{APIMessage, ApiPlanContext}

import upickle.default.ReadWriter

/** 供后端服务批量记录通知。 */
final case class RecordBulkNotificationsPrivateAPIMessage(
    requests: Vector[CreateNotificationRequest]
) extends APIMessage[Vector[Notification]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Notification]] =
    requests.foldLeft(IO.pure(Vector.empty[Notification])) { (acc, request) =>
      acc.flatMap(notifications =>
        RecordNotificationPrivateAPIMessage(request).plan(context).map(notification => notifications :+ notification)
      )
    }
