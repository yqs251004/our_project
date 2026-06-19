package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** MahjongCoreShowcaseModeView 表示麻将核心演示模式视图 的前端展示视图。 */

final case class MahjongCoreShowcaseModeView(enabled: Boolean)

object MahjongCoreShowcaseModeView:
  given ReadWriter[MahjongCoreShowcaseModeView] = macroRW
