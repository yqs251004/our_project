package riichinexus.microservices.auth.objects.session

/** 游客访问会话的稳定标识符。
  *
  * 使用独立值类型包住字符串，避免和账号令牌、玩家 ID 等其他身份字符串混用。
  */
final case class GuestSessionId(value: String)
