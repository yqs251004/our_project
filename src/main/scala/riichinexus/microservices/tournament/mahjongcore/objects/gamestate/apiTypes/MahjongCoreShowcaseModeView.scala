package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 麻将核心演示模式当前开关状态。
  *
  * 前端用它决定是否展示演示/调试入口，后端用同一状态控制部分展示用数据路径。
  */
final case class MahjongCoreShowcaseModeView(enabled: Boolean)

object MahjongCoreShowcaseModeView:
  given ReadWriter[MahjongCoreShowcaseModeView] = macroRW
