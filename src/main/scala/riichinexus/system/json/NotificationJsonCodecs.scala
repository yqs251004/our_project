package riichinexus.system.json

import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.system.json.JsonCodecSupport.stringEnumReadWriter
import upickle.default.ReadWriter

object NotificationJsonCodecs:
  given ReadWriter[NotificationType] =
    stringEnumReadWriter(NotificationType.fromString, NotificationType.toString)
