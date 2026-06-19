package riichinexus.microservices.tournament.domain.stage.functions.rules.progression


import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRule
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat

/** AdvancementRuleFunctions 提供AdvancementRule相关的领域计算、校验和转换函数。 */

private[tournament] object AdvancementRuleFunctions:
  def defaultFor(format: TournamentFormat): AdvancementRule =
    format match
      case TournamentFormat.Swiss =>
        AdvancementRule(AdvancementRuleType.SwissCut, cutSize = Some(16))
      case TournamentFormat.Knockout =>
        AdvancementRule(AdvancementRuleType.KnockoutElimination, targetTableCount = Some(1))
      case TournamentFormat.RoundRobin =>
        AdvancementRule(AdvancementRuleType.ScoreThreshold, thresholdScore = Some(0))
      case TournamentFormat.Finals =>
        AdvancementRule(AdvancementRuleType.KnockoutElimination, targetTableCount = Some(1))
      case TournamentFormat.Custom =>
        AdvancementRule(AdvancementRuleType.Custom, note = Some("custom policy"))
