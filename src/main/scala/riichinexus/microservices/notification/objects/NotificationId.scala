package riichinexus.microservices.notification.objects

/** 系统通知的稳定标识符。
  *
  * 通知 ID 使用带前缀的短 UUID，既便于日志辨认，又避免和其他业务聚合 ID 混淆。
  */
final case class NotificationId(value: String)
