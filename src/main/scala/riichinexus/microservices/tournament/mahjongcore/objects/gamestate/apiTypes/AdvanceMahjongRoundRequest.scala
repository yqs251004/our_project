package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import upickle.default.*

final case class AdvanceMahjongRoundRequest(
    showcaseMode: Option[Boolean] = None
)

object AdvanceMahjongRoundRequest:
  given ReadWriter[AdvanceMahjongRoundRequest] = macroRW
