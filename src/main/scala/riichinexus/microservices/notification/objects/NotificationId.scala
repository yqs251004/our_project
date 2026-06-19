package riichinexus.microservices.notification.objects

import java.util.UUID

import upickle.default.{ReadWriter, readwriter}

/** NotificationId 表示通知标识符 的稳定字符串标识符。 */

final case class NotificationId(value: String)

object NotificationId:
  given ReadWriter[NotificationId] =
    readwriter[String].bimap[NotificationId](_.value, NotificationId(_))

  def next(): NotificationId =
    NotificationId(s"notification-${UUID.randomUUID().toString.take(8)}")
