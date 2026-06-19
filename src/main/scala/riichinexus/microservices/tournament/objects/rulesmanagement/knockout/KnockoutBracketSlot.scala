package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** KnockoutBracketSlot 表示前后端共享的KnockoutBracketSlot 数据结构，包含seed、玩家 ID、bye、sourceMatchId、sourcePlacement。 */

final case class KnockoutBracketSlot(
    seed: Int,
    playerId: Option[PlayerId],
    bye: Boolean = false,
    sourceMatchId: Option[String] = None,
    sourcePlacement: Option[Int] = None
) derives ReadWriter
