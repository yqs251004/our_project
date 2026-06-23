package riichinexus.system.objects.`private`

/** 后端内部创建通知时声明来源微服务的稳定类型。
  *
  * 这个类型约束各微服务提交通知请求时可使用的 sourceService；落库、实时事件和 HTTP 序列化仍使用 toString 给出的稳定字符串。
  */
enum NotificationSourceService:
  case Club
  case Tournament
  case OpsAnalytics

object NotificationSourceService:
  def toString(sourceService: NotificationSourceService): String =
    sourceService match
      case NotificationSourceService.Club         => "club"
      case NotificationSourceService.Tournament   => "tournament"
      case NotificationSourceService.OpsAnalytics => "opsanalytics"

  def fromString(value: String): NotificationSourceService =
    value match
      case "club"         => NotificationSourceService.Club
      case "tournament"   => NotificationSourceService.Tournament
      case "opsanalytics" => NotificationSourceService.OpsAnalytics
      case other          => throw IllegalArgumentException(s"Unknown notification source service: $other")
