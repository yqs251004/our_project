package riichinexus.microservices.notification.objects

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class Notification(
    id: NotificationId,
    recipientPlayerId: PlayerId,
    notificationType: String,
    title: String,
    body: String,
    severity: String,
    sourceService: String,
    sourceType: String,
    sourceId: String,
    actionUrl: Option[String] = None,
    readAt: Option[Instant] = None,
    createdAt: Instant,
    expiresAt: Option[Instant] = None,
    objects: Map[String, String] = Map.empty
) derives ReadWriter
