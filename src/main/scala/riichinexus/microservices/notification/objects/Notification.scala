package riichinexus.microservices.notification.objects

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 发给玩家的一条持久化系统通知。
  *
  * 通知保留接收人、类型、内容、严重级别、来源服务/对象、可选操作链接和已读/过期时间，用于通知中心和实时事件补偿。
  */
final case class Notification(
    id: NotificationId,
    recipientPlayerId: PlayerId,
    notificationType: NotificationType,
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
)

object Notification:
  given ReadWriter[Notification] = macroRW
