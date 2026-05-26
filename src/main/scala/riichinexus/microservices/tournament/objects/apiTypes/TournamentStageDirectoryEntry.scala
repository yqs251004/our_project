package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.{StageFormat, StageStatus, TournamentStageId}
import riichinexus.microservices.tournament.objects.TournamentFormat

final case class TournamentStageDirectoryEntry(
    stageId: String,
    name: String,
    format: TournamentFormat,
    order: Int,
    status: String,
    currentRound: Int,
    roundCount: Int,
    schedulingPoolSize: Int,
    pendingTablePlanCount: Int,
    scheduledTableCount: Int
) derives CanEqual

object TournamentStageDirectoryEntry:
  def apply(
      stageId: TournamentStageId,
      name: String,
      format: StageFormat,
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
      format = TournamentFormat.fromStageFormat(format),
      order = order,
      status = status.toString,
      currentRound = currentRound,
      roundCount = roundCount,
      schedulingPoolSize = schedulingPoolSize,
      pendingTablePlanCount = pendingTablePlanCount,
      scheduledTableCount = scheduledTableCount
    )
