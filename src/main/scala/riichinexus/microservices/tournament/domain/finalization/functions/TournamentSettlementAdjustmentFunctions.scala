package riichinexus.microservices.tournament.domain.finalization.functions


import riichinexus.microservices.tournament.objects.finalization.TournamentSettlementAdjustment

/** TournamentSettlementAdjustmentFunctions 提供赛事结算调整相关的领域计算、校验和转换函数。 */

private[tournament] object TournamentSettlementAdjustmentFunctions:
  def validate(adjustment: TournamentSettlementAdjustment): Unit =
    require(adjustment.label.trim.nonEmpty, "Tournament settlement adjustment label cannot be empty")
