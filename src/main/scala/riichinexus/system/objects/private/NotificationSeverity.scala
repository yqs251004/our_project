package riichinexus.system.objects.`private`

/** 后端内部创建通知时使用的稳定严重级别。
  *
  * 这个类型约束各微服务提交通知请求时可使用的 severity；落库、实时事件和 HTTP 序列化仍使用 toString 给出的稳定字符串。
  */
enum NotificationSeverity:
  case Info
  case Success
  case Warning

object NotificationSeverity:
  def toString(severity: NotificationSeverity): String =
    severity match
      case NotificationSeverity.Info    => "info"
      case NotificationSeverity.Success => "success"
      case NotificationSeverity.Warning => "warning"

  def fromString(value: String): NotificationSeverity =
    value match
      case "info"    => NotificationSeverity.Info
      case "success" => NotificationSeverity.Success
      case "warning" => NotificationSeverity.Warning
      case other     => throw IllegalArgumentException(s"Unknown notification severity: $other")
