package riichinexus.system.json

import riichinexus.microservices.notification.objects.{NotificationId, NotificationType}
import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType}
import riichinexus.system.json.JsonCodecSupport.stringEnumReadWriter
import upickle.default.{ReadWriter, readwriter}

object NotificationJsonCodecs:
  given ReadWriter[NotificationId] =
    readwriter[String].bimap[NotificationId](_.value, NotificationId(_))

  given ReadWriter[NotificationType] =
    stringEnumReadWriter(NotificationType.fromString, NotificationType.toString)

  given ReadWriter[NotificationSeverity] =
    stringEnumReadWriter(NotificationSeverity.fromString, NotificationSeverity.toString)

  given ReadWriter[NotificationSourceService] =
    stringEnumReadWriter(NotificationSourceService.fromString, NotificationSourceService.toString)

  given ReadWriter[NotificationSourceType] =
    stringEnumReadWriter(NotificationSourceType.fromString, NotificationSourceType.toString)
