package riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression

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
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRule
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat

object AdvancementRuleFunctions:
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
