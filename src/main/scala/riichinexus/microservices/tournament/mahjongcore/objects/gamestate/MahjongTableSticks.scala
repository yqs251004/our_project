package riichinexus.microservices.tournament.mahjongcore.objects.gamestate

import upickle.default.{ReadWriter, macroRW}

/** 汇总桌面棒数状态；honba 表示本场棒，riichi 表示立直供托棒，两者结算规则不同。 */
final case class MahjongTableSticks(
    honba: Int = 0,
    riichi: Int = 0
)

object MahjongTableSticks:
  given ReadWriter[MahjongTableSticks] = macroRW
