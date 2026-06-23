package riichinexus.microservices.notification.objects.`private`

import java.time.Instant

import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.system.objects.`private`.{NotificationSeverity, NotificationSourceService, NotificationSourceType}
import riichinexus.system.json.JsonCodecs.given
import riichinexus.system.json.NotificationJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 其他后端服务调用通知私有 API 时提交的创建请求。
  *
  * 调用方提供接收人、通知类型、内容、来源定位、可选动作链接和对象映射，通知服务负责补默认严重级别并持久化。
  */
final case class CreateNotificationRequest(
    recipientPlayerId: String,
    notificationType: NotificationType,
    title: String,
    body: String,
    severity: Option[NotificationSeverity] = None,
    sourceService: NotificationSourceService,
    sourceType: NotificationSourceType,
    sourceId: String,
    actionUrl: Option[String] = None,
    expiresAt: Option[Instant] = None,
    objects: Map[String, String] = Map.empty
)

object CreateNotificationRequest:
  given ReadWriter[CreateNotificationRequest] = macroRW
