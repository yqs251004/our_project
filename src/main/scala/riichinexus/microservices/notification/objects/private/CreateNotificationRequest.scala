package riichinexus.microservices.notification.objects.`private`

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** CreateNotificationRequest 表示后端服务创建通知时传给 notification 私有 API 的请求内容。 */

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
