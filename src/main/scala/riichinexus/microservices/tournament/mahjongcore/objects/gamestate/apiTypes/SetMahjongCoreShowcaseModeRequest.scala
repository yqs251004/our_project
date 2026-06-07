package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import upickle.default.*

final case class SetMahjongCoreShowcaseModeRequest(enabled: Boolean)

object SetMahjongCoreShowcaseModeRequest:
  given ReadWriter[SetMahjongCoreShowcaseModeRequest] = macroRW
