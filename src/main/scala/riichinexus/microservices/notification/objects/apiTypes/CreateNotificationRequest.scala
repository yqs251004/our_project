package riichinexus.microservices.notification.objects.apiTypes

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CreateNotificationRequest(
    recipientPlayerId: String,
    notificationType: String,
    title: String,
    body: String,
    severity: Option[String] = None,
    sourceService: String,
    sourceType: String,
    sourceId: String,
    actionUrl: Option[String] = None,
    expiresAt: Option[Instant] = None,
    objects: Map[String, String] = Map.empty
) derives ReadWriter
