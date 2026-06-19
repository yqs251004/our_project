package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import java.time.Instant

import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentStatus}
import upickle.default.{ReadWriter, macroRW}

/** PublicScheduleView 表示公开赛程视图 的前端展示视图，包含赛事 ID、tournamentName、tournamentStatus、阶段 ID、stageName、stageStatus等。 */

final case class PublicScheduleView(
    tournamentId: String,
    tournamentName: String,
    tournamentStatus: TournamentStatus,
    stageId: String,
    stageName: String,
    stageStatus: StageStatus,
    currentRound: Int,
    roundCount: Int,
    startsAt: String,
    endsAt: String,
    tableCount: Int,
    activeTableCount: Int,
    pendingTablePlanCount: Int,
    participantCount: Int,
    whitelistCount: Int
)

object PublicScheduleView:
  given ReadWriter[PublicScheduleView] = macroRW

  def apply(
      tournamentId: TournamentId,
      tournamentName: String,
      tournamentStatus: TournamentStatus,
      stageId: TournamentStageId,
      stageName: String,
      stageStatus: StageStatus,
      currentRound: Int,
      roundCount: Int,
      startsAt: Instant,
      endsAt: Instant,
      tableCount: Int,
      activeTableCount: Int,
      pendingTablePlanCount: Int,
      participantCount: Int,
      whitelistCount: Int
  ): PublicScheduleView =
    PublicScheduleView(
      tournamentId = tournamentId.value,
      tournamentName = tournamentName,
      tournamentStatus = tournamentStatus,
      stageId = stageId.value,
      stageName = stageName,
      stageStatus = stageStatus,
      currentRound = currentRound,
      roundCount = roundCount,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      tableCount = tableCount,
      activeTableCount = activeTableCount,
      pendingTablePlanCount = pendingTablePlanCount,
      participantCount = participantCount,
      whitelistCount = whitelistCount
    )
