package riichinexus.microservices.tournament.mahjongcore.objects.gamestate.apiTypes

import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class AdvanceMahjongRoundRequest(
    playerId: Option[String] = None,
    showcaseMode: Option[Boolean] = None
)

object AdvanceMahjongRoundRequest:
  given ReadWriter[AdvanceMahjongRoundRequest] = macroRW
