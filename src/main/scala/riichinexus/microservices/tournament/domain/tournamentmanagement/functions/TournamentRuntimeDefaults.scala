package riichinexus.microservices.tournament.domain.tournamentmanagement.functions

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
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRuleType

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.AdvancementRuleFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*

object TournamentRuntimeDefaults:
  val settlementPayoutRatios: Vector[Double] =
    Vector(0.5, 0.3, 0.2)

  def normalizeStage(stage: TournamentStage): TournamentStage =
    if stage.advancementRule.ruleType == AdvancementRuleType.Custom &&
        stage.advancementRule.note.contains("unconfigured") &&
        stage.advancementRule.templateKey.isEmpty
    then stage.copy(advancementRule = AdvancementRuleFunctions.defaultFor(stage.format))
    else stage
