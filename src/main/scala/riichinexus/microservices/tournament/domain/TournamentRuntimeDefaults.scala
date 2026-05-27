package riichinexus.microservices.tournament.domain

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*

object TournamentRuntimeDefaults:
  val settlementPayoutRatios: Vector[Double] =
    Vector(0.5, 0.3, 0.2)

  def normalizeStage(stage: TournamentStage): TournamentStage =
    if stage.advancementRule.ruleType == AdvancementRuleType.Custom &&
        stage.advancementRule.note.contains("unconfigured") &&
        stage.advancementRule.templateKey.isEmpty
    then stage.copy(advancementRule = AdvancementRule.defaultFor(stage.format))
    else stage
