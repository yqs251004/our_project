package riichinexus.microservices.tournament.objects.paifumanagement

/** PaifuRound 表示前后端共享的牌谱小局 数据结构，包含descriptor、玩家、timeline、result。 */

final case class PaifuRound(
    descriptor: KyokuDescriptor,
    players: Vector[PaifuRoundPlayer],
    timeline: PaifuTimeline,
    result: AgariResult
)
