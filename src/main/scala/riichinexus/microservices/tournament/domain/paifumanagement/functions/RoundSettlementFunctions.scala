package riichinexus.microservices.tournament.domain.paifumanagement.functions

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import riichinexus.microservices.tournament.objects.paifumanagement.RoundSettlement

object RoundSettlementFunctions:
  def validate(settlement: RoundSettlement): Unit =
    require(settlement.riichiSticksDelta >= 0, "Riichi sticks delta must be non-negative")
    require(settlement.honbaPayment >= 0, "Honba payment must be non-negative")
    require(settlement.riichiSticksDelta % 1000 == 0, "Riichi sticks delta must be a multiple of 1000")
    require(settlement.honbaPayment % 100 == 0, "Honba payment must be a multiple of 100")
    require(
      settlement.notes.forall(_.trim.nonEmpty),
      "Round settlement notes cannot contain blank entries"
    )
