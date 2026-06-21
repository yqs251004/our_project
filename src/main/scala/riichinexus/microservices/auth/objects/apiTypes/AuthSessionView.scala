package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 恢复认证会话时返回给前端的会话摘要。
  *
  * 它确认当前 token 对应的用户、展示名和角色标记，但不重复返回 token 本身，适合页面刷新后的状态重建。
  */
final case class AuthSessionView(
    userId: String,
    username: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags
)

object AuthSessionView:
  given ReadWriter[AuthSessionView] = macroRW
