package riichinexus.microservices.notification.objects.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class NotificationListQuery(
    unreadOnly: Option[Boolean] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
