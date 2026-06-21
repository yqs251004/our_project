package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 当前会话中已注册玩家身份的前端摘要。
  *
  * 它把认证用户 ID、玩家档案 ID 和昵称合在一起，方便导航、个人面板和权限入口使用同一份身份信息。
  */
final case class CurrentSessionPlayerView(
    id: String,
    userId: String,
    nickname: String
)

object CurrentSessionPlayerView:
  given ReadWriter[CurrentSessionPlayerView] = macroRW
