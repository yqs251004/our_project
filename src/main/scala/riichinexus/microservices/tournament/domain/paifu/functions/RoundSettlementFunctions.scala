package riichinexus.microservices.tournament.domain.paifu.functions


import riichinexus.microservices.tournament.objects.paifumanagement.RoundSettlement

/** RoundSettlementFunctions 提供小局结算相关的领域计算、校验和转换函数。 */

private[tournament] object RoundSettlementFunctions:
  def validate(settlement: RoundSettlement): Unit =
    require(settlement.riichiSticksDelta >= 0, "Riichi sticks delta must be non-negative")
    require(settlement.honbaPayment >= 0, "Honba payment must be non-negative")
    require(settlement.riichiSticksDelta % 1000 == 0, "Riichi sticks delta must be a multiple of 1000")
    require(settlement.honbaPayment % 100 == 0, "Honba payment must be a multiple of 100")
