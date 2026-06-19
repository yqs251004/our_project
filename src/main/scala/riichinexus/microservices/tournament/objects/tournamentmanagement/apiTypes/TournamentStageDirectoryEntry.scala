package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStageId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentFormat}

/** TournamentStageDirectoryEntry 表示前后端共享的赛事阶段目录条目 数据结构，包含阶段 ID、名称、format、order、状态、currentRound等。 */

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
