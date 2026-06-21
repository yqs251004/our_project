package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 登出接口返回的简短确认消息。
  *
  * 该类型让前端统一处理退出后的提示文案，同时保持响应结构稳定，便于后续扩展更多登出结果字段。
  */
final case class LogoutResponse(
    message: String
)

object LogoutResponse:
  given ReadWriter[LogoutResponse] = macroRW
