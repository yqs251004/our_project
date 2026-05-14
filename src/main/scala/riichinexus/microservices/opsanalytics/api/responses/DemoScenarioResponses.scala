package riichinexus.microservices.opsanalytics.api.responses

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.api.responses.*

enum DemoScenarioVariant derives CanEqual:
  case Basic
  case Leaderboard
  case Appeal

enum DemoScenarioActionCode derives CanEqual:
  case RefreshScenario
  case ResetScenario
  case ArchiveNextTable
  case FileOpenAppeal
  case ResolveOldestAppeal

final case class DemoScenarioDashboardSummary(
    sampleSize: Int,
    dealInRate: Double,
    winRate: Double,
    averageWinPoints: Double,
    riichiRate: Double,
    averagePlacement: Double,
    topFinishRate: Double,
    lastUpdatedAt: Instant
) derives CanEqual

final case class DemoScenarioAdvancedStatsSummary(
    sampleSize: Int,
    defenseStability: Double,
    ukeireExpectation: Double,
    averageShantenImprovement: Double,
    callAggressionRate: Double,
    riichiConversionRate: Double,
    pressureDefenseRate: Double,
    postRiichiFoldRate: Double,
    lastUpdatedAt: Instant
) derives CanEqual

final case class DemoScenarioPlayerView(
    playerId: PlayerId,
    userId: String,
    nickname: String,
    currentRank: RankSnapshot,
    elo: Int,
    status: PlayerStatus,
    clubIds: Vector[ClubId],
    isSuperAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isClubAdmin: Boolean,
    dashboard: Option[DemoScenarioDashboardSummary],
    advancedStats: Option[DemoScenarioAdvancedStatsSummary]
) derives CanEqual

final case class DemoScenarioClubView(
    clubId: ClubId,
    name: String,
    memberIds: Vector[PlayerId],
    adminIds: Vector[PlayerId],
    powerRating: Double,
    totalPoints: Int,
    treasuryBalance: Long,
    pointPool: Int,
    honorTitles: Vector[String],
    dashboard: Option[DemoScenarioDashboardSummary],
    advancedStats: Option[DemoScenarioAdvancedStatsSummary]
) derives CanEqual

final case class DemoScenarioTableSeatView(
    seat: SeatWind,
    playerId: PlayerId,
    nickname: String,
    clubId: Option[ClubId],
    initialPoints: Int,
    ready: Boolean,
    disconnected: Boolean
) derives CanEqual

final case class DemoScenarioTableView(
    tableId: TableId,
    tableNo: Int,
    stageRoundNumber: Int,
    status: TableStatus,
    startedAt: Option[Instant],
    endedAt: Option[Instant],
    hasMatchRecord: Boolean,
    hasPaifu: Boolean,
    hasAppeal: Boolean,
    seats: Vector[DemoScenarioTableSeatView]
) derives CanEqual

final case class DemoScenarioTournamentView(
    tournamentId: TournamentId,
    name: String,
    status: TournamentStatus,
    stageId: TournamentStageId,
    stageName: String,
    tableIds: Vector[TableId],
    archivedTableIds: Vector[TableId],
    tables: Vector[DemoScenarioTableView]
) derives CanEqual

final case class DemoScenarioReadiness(
    dashboardOwnersExpected: Int,
    dashboardOwnersReady: Int,
    advancedStatsOwnersExpected: Int,
    advancedStatsOwnersReady: Int,
    pendingOutboxCount: Int,
    deadLetterOutboxCount: Int,
    pendingAdvancedStatsTaskCount: Int,
    deadLetterAdvancedStatsTaskCount: Int
) derives CanEqual

final case class DemoScenarioApiRequest(
    method: String,
    path: String,
    description: String
) derives CanEqual

final case class DemoScenarioWidgetMetric(
    key: String,
    label: String,
    value: Double,
    formattedValue: String
) derives CanEqual

final case class DemoScenarioWidgetCount(
    key: String,
    label: String,
    count: Int
) derives CanEqual

final case class DemoScenarioWidgets(
    variant: DemoScenarioVariant,
    generatedAt: Instant,
    headlineMetrics: Vector[DemoScenarioWidgetMetric],
    playerEloSeries: Vector[DemoScenarioWidgetMetric],
    clubPowerSeries: Vector[DemoScenarioWidgetMetric],
    playerLeaderboardPreview: Vector[DemoScenarioWidgetMetric],
    clubLeaderboardPreview: Vector[DemoScenarioWidgetMetric],
    tableStatusBreakdown: Vector[DemoScenarioWidgetCount],
    readinessBreakdown: Vector[DemoScenarioWidgetCount]
) derives CanEqual

final case class DemoScenarioActionSpec(
    code: DemoScenarioActionCode,
    label: String,
    description: String,
    method: String,
    path: String,
    enabled: Boolean
) derives CanEqual

final case class DemoScenarioActionResult(
    variant: DemoScenarioVariant,
    action: DemoScenarioActionCode,
    message: String,
    snapshot: DemoScenarioSnapshot,
    widgets: DemoScenarioWidgets
) derives CanEqual

final case class DemoScenarioGuideStep(
    title: String,
    description: String,
    request: Option[DemoScenarioApiRequest] = None
) derives CanEqual

final case class DemoScenarioGuide(
    variant: DemoScenarioVariant,
    title: String,
    summary: String,
    steps: Vector[DemoScenarioGuideStep],
    frontendSections: Vector[String],
    presenterNotes: Vector[String]
) derives CanEqual

final case class DemoScenarioSnapshot(
    variant: DemoScenarioVariant,
    seededAt: Instant,
    guestSessionId: Option[GuestSessionId],
    recommendedOperatorId: PlayerId,
    players: Vector[DemoScenarioPlayerView],
    clubs: Vector[DemoScenarioClubView],
    tournament: DemoScenarioTournamentView,
    publicSchedules: Vector[PublicScheduleView],
    publicClubDirectory: Vector[PublicClubDirectoryEntry],
    playerLeaderboard: Vector[PlayerLeaderboardEntry],
    clubLeaderboard: Vector[ClubLeaderboardEntry],
    recommendedRequests: Vector[DemoScenarioApiRequest],
    availableActions: Vector[DemoScenarioActionSpec],
    readiness: DemoScenarioReadiness
) derives CanEqual

object DemoScenarioResponses:
  export DemoScenarioResponseCodecs.given
