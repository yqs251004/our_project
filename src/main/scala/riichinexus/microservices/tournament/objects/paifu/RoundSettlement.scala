package riichinexus.microservices.tournament.objects.paifu

/** 一小局中除玩家基础得失分外的结算补充。
  *
  * 它记录立直棒变化、本场支付和结果标签，帮助前端解释为什么最终点数发生额外变化。
  */
final case class RoundSettlement(
    riichiSticksDelta: Int = 0,
    honbaPayment: Int = 0,
    notes: Vector[RoundSettlementNote] = Vector.empty
)
