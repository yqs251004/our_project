package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import upickle.default.*

final case class MahjongCoreShowcaseModeView(enabled: Boolean)

object MahjongCoreShowcaseModeView:
  given ReadWriter[MahjongCoreShowcaseModeView] = macroRW
