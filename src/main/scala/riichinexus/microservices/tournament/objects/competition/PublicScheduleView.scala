package riichinexus.microservices.tournament.objects.competition

import java.time.Instant

import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.objects.stage.lifecycle.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.system.json.TournamentJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 公共赛程列表中的赛事阶段摘要。
  *
  * 该视图把赛事状态、阶段轮次、牌桌数量、待排计划和参赛规模放在一起，供大厅展示近期可关注的阶段进度。
  */
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
