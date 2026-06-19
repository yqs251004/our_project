package riichinexus.microservices.auth.objects.apiTypes


import upickle.default.{ReadWriter, macroRW}

/** CurrentSessionGuestSessionView 表示当前会话游客会话视图 的前端展示视图。 */

final case class CurrentSessionGuestSessionView(
    id: String,
    displayName: String
)

object CurrentSessionGuestSessionView:
  given ReadWriter[CurrentSessionGuestSessionView] = macroRW
