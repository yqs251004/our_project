package riichinexus.microservices.notification.api.`private`

import cats.effect.IO
import cats.syntax.all.*
import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.notification.objects.apiTypes.CreateNotificationRequest
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CreateBulkNotificationsPrivateAPIMessage(
    requests: Vector[CreateNotificationRequest]
) extends APIMessage[Vector[Notification]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[Notification]] =
    requests.traverse(request => CreateNotificationPrivateAPIMessage(request).plan(context))
