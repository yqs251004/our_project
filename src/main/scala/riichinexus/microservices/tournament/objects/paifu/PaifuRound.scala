package riichinexus.microservices.tournament.objects.paifu

/** 牌谱中的一小局完整记录。
  *
  * 小局由局定位、四位玩家、全局时间线和最终结算组成，是回放和统计分析的基本切片。
  */
final case class PaifuRound(
    descriptor: KyokuDescriptor,
    players: Vector[PaifuRoundPlayer],
    timeline: PaifuTimeline,
    result: AgariResult
)
