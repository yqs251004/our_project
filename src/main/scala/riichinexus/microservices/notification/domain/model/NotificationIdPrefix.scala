package riichinexus.microservices.notification.domain.model

/** 通知微服务内部生成通知 ID 时使用的稳定前缀。
  *
  * 该类型只约束后端 ID 生成器可选的前缀，不属于公开 API，也不需要前端镜像。
  */
private[notification] enum NotificationIdPrefix:
  case Notification

object NotificationIdPrefix:
  def toString(prefix: NotificationIdPrefix): String =
    prefix match
      case NotificationIdPrefix.Notification => "notification"
