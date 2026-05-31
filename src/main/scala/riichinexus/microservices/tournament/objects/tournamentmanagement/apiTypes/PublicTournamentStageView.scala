package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentFormat}
import riichinexus.microservices.tournament.objects.rulesmanagement.swiss.SwissRuleConfig

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.StageRankingSnapshot
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentFormat}
import upickle.default.*

final case class PublicTournamentStageView(
    stageId: String,
    name: String,
    format: TournamentFormat,
    order: Int,
    status: StageStatus,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    tableCount: Int,
    archivedTableCount: Int,
    pendingTablePlanCount: Int,
    standings: Option[StageRankingSnapshot],
    bracket: Option[KnockoutBracketSnapshot],
    advancementRule: AdvancementRule = AdvancementRule(AdvancementRuleType.Custom, note = Some("unconfigured")),
    swissRule: Option[SwissRuleConfig] = None,
    knockoutRule: Option[KnockoutRuleConfig] = None
) derives CanEqual

object PublicTournamentStageView:
  given ReadWriter[PublicTournamentStageView] = macroRW

  def apply(
      stageId: TournamentStageId,
      name: String,
      format: TournamentFormat,
      order: Int,
      status: StageStatus,
      currentRound: Int,
      roundCount: Int,
      schedulingPoolSize: Int,
      tableCount: Int,
      archivedTableCount: Int,
      pendingTablePlanCount: Int,
      standings: Option[StageRankingSnapshot],
      bracket: Option[KnockoutBracketSnapshot],
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig]
  ): PublicTournamentStageView =
    PublicTournamentStageView(
      stageId = stageId.value,
      name = name,
      format = format,
      order = order,
      status = status,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      tableCount = tableCount,
      archivedTableCount = archivedTableCount,
      pendingTablePlanCount = pendingTablePlanCount,
      standings = standings,
      bracket = bracket,
      advancementRule = advancementRule,
      swissRule = swissRule,
      knockoutRule = knockoutRule
    )
