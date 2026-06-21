package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 注册正式账号并创建玩家展示身份的请求体。
  *
  * 用户名和密码用于生成登录凭证，`displayName` 会作为新玩家在大厅、赛事和俱乐部中的默认显示名。
  */
final case class RegisterAccountRequest(
    username: String,
    password: String,
    displayName: String
)

object RegisterAccountRequest:
  given ReadWriter[RegisterAccountRequest] = macroRW
