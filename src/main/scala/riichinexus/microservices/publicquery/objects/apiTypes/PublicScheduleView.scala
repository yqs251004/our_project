package riichinexus.microservices.publicquery.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{StageStatus, TournamentId, TournamentStageId, TournamentStatus}
import upickle.default.*

final case class PublicScheduleView(
    tournamentId: String,
    tournamentName: String,
    tournamentStatus: String,
    stageId: String,
    stageName: String,
    stageStatus: String,
    currentRound: Int,
    roundCount: Int,
    startsAt: String,
    endsAt: String,
    tableCount: Int,
    activeTableCount: Int,
    pendingTablePlanCount: Int,
    participantCount: Int,
    whitelistCount: Int
) derives CanEqual

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
      tournamentStatus = tournamentStatus.toString,
      stageId = stageId.value,
      stageName = stageName,
      stageStatus = stageStatus.toString,
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
