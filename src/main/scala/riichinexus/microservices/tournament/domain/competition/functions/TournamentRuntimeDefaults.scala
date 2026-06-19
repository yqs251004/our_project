package riichinexus.microservices.tournament.domain.competition.functions

import riichinexus.microservices.tournament.domain.stage.functions.rules.progression.AdvancementRuleFunctions

import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRuleType

import riichinexus.microservices.tournament.domain.stage.model.TournamentStage

/** TournamentRuntimeDefaults 提供赛事运行时默认值 相关的领域计算、校验和转换函数。 */

private[tournament] object TournamentRuntimeDefaults:
  val settlementPayoutRatios: Vector[Double] =
    Vector(0.5, 0.3, 0.2)

  def normalizeStage(stage: TournamentStage): TournamentStage =
    if stage.advancementRule.ruleType == AdvancementRuleType.Custom &&
        stage.advancementRule.note.contains("unconfigured") &&
        stage.advancementRule.templateKey.isEmpty
    then stage.copy(advancementRule = AdvancementRuleFunctions.defaultFor(stage.format))
    else stage
