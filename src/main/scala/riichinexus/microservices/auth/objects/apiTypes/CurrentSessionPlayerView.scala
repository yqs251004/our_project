package riichinexus.microservices.auth.objects.apiTypes


import upickle.default.{ReadWriter, macroRW}

/** CurrentSessionPlayerView 表示当前会话玩家视图 的前端展示视图。 */

final case class CurrentSessionPlayerView(
    id: String,
    userId: String,
    nickname: String
)

object CurrentSessionPlayerView:
  given ReadWriter[CurrentSessionPlayerView] = macroRW
