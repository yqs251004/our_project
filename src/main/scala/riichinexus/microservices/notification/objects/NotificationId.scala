package riichinexus.microservices.notification.objects

import java.util.UUID

import upickle.default.{ReadWriter, readwriter}

/** 系统通知的稳定标识符。
  *
  * 通知 ID 使用带前缀的短 UUID，既便于日志辨认，又避免和其他业务聚合 ID 混淆。
  */
final case class NotificationId(value: String)

object NotificationId:
  given ReadWriter[NotificationId] =
    readwriter[String].bimap[NotificationId](_.value, NotificationId(_))

  def next(): NotificationId =
    NotificationId(s"notification-${UUID.randomUUID().toString.take(8)}")
