package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** KnockoutBracketResult 表示前后端共享的KnockoutBracket结果 数据结构，包含玩家 ID、名次、最终点数、advanced。 */

final case class KnockoutBracketResult(
    playerId: PlayerId,
    placement: Int,
    finalPoints: Int,
    advanced: Boolean
) derives ReadWriter
