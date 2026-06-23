package riichinexus.microservices.tournament.objects.stage

import upickle.default.{ReadWriter, macroRW}

import riichinexus.microservices.tournament.objects.identity.TournamentStageId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.stage.lifecycle.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentFormat

/** 阶段目录或下拉选择器使用的轻量条目。
  *
  * 它只保留阶段身份、排序、状态、轮次和排桌计数，适合快速导航而不拉取排名、对阵或阵容详情。
  */
final case class TournamentStageDirectoryEntry(
    stageId: String,
    name: String,
    format: TournamentFormat,
    order: Int,
    status: StageStatus,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int
)

object TournamentStageDirectoryEntry:
  given ReadWriter[TournamentStageDirectoryEntry] = macroRW

  def apply(
      stageId: TournamentStageId,
      name: String,
      format: TournamentFormat,
      order: Int,
      status: StageStatus,
      currentRound: Int,
      roundCount: Int,
      schedulingPoolSize: Int,
      pendingTablePlanCount: Int,
      scheduledTableCount: Int
  ): TournamentStageDirectoryEntry =
    TournamentStageDirectoryEntry(
      stageId = stageId.value,
      name = name,
      format = format,
      order = order,
      status = status,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      pendingTablePlanCount = pendingTablePlanCount,
      scheduledTableCount = scheduledTableCount
    )
