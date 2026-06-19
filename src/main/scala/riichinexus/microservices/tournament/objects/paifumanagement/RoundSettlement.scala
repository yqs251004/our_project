package riichinexus.microservices.tournament.objects.paifumanagement

/** RoundSettlement 表示前后端共享的小局结算 数据结构，包含riichiSticksDelta、honbaPayment、notes。 */

final case class RoundSettlement(
    riichiSticksDelta: Int = 0,
    honbaPayment: Int = 0,
    notes: Vector[RoundSettlementNote] = Vector.empty
)
