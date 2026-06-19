package riichinexus.microservices.tournament.domain.matchrecord.functions

import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecordSeatResult


/** MatchRecordSeatResultFunctions 提供对局记录座位结果相关的领域计算、校验和转换函数。 */


private[tournament] object MatchRecordSeatResultFunctions:
  def validate(result: MatchRecordSeatResult): Unit =
    require(result.placement >= 1 && result.placement <= 4, "Placement must be between 1 and 4")
