package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 当前会话中游客身份的前端安全摘要。
  *
  * 这里只暴露游客会话 ID 和展示名，供大厅继续识别临时用户，不泄露设备指纹、有效期或撤销状态等内部字段。
  */
final case class CurrentSessionGuestSessionView(
    id: String,
    displayName: String
)

object CurrentSessionGuestSessionView:
  given ReadWriter[CurrentSessionGuestSessionView] = macroRW
