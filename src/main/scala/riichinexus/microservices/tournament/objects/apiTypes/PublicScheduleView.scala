package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.objects.{StageStatus, TournamentStatus}
import upickle.default.*

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
