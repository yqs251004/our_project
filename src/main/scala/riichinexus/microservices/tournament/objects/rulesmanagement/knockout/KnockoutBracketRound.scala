package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

import upickle.default.ReadWriter

/** KnockoutBracketRound 表示前后端共享的KnockoutBracket小局 数据结构，包含roundNumber、label、matches。 */

final case class KnockoutBracketRound(
    roundNumber: Int,
    label: String,
    matches: Vector[KnockoutBracketMatch]
) derives ReadWriter
