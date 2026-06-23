package riichinexus.microservices.notification.domain.functions

import java.util.UUID

import riichinexus.microservices.notification.domain.model.NotificationIdPrefix
import riichinexus.microservices.notification.objects.NotificationId

/** NotificationIdGenerator 负责生成通知微服务内部使用的领域标识符。 */
private[notification] object NotificationIdGenerator:
  private def nextId(prefix: NotificationIdPrefix): String =
    s"${NotificationIdPrefix.toString(prefix)}-${UUID.randomUUID().toString.take(8)}"

  def notificationId(): NotificationId =
    NotificationId(nextId(NotificationIdPrefix.Notification))
