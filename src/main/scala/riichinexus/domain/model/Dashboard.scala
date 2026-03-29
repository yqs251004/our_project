package riichinexus.domain.model

import java.time.Instant

enum DashboardOwner derives CanEqual:
  case Player(playerId: PlayerId)
  case Club(clubId: ClubId)

final case class Dashboard(
    owner: DashboardOwner,
    sampleSize: Int,
    dealInRate: Double,
    winRate: Double,
    averageWinPoints: Double,
    riichiRate: Double,
    averagePlacement: Double,
    topFinishRate: Double,
    lastUpdatedAt: Instant,
    version: Int = 0
) derives CanEqual

object Dashboard:
  def empty(owner: DashboardOwner, at: Instant): Dashboard =
    Dashboard(
      owner = owner,
      sampleSize = 0,
      dealInRate = 0.0,
      winRate = 0.0,
      averageWinPoints = 0.0,
      riichiRate = 0.0,
      averagePlacement = 0.0,
      topFinishRate = 0.0,
      lastUpdatedAt = at
    )

final case class AdvancedStatsBoard(
    owner: DashboardOwner,
    sampleSize: Int,
    defenseStability: Double,
    ukeireExpectation: Double,
    averageShantenImprovement: Double,
    callAggressionRate: Double,
    riichiConversionRate: Double,
    pressureDefenseRate: Double,
    postRiichiFoldRate: Double,
    shantenTrajectory: Vector[Double],
    calculatorVersion: Int,
    strictRoundSampleSize: Int,
    exactUkeireSampleRate: Double,
    exactDefenseSampleRate: Double,
    lastUpdatedAt: Instant,
    version: Int = 0
) derives CanEqual

object AdvancedStatsBoard:
  val CurrentCalculatorVersion = 2

  def empty(owner: DashboardOwner, at: Instant): AdvancedStatsBoard =
    AdvancedStatsBoard(
      owner = owner,
      sampleSize = 0,
      defenseStability = 0.0,
      ukeireExpectation = 0.0,
      averageShantenImprovement = 0.0,
      callAggressionRate = 0.0,
      riichiConversionRate = 0.0,
      pressureDefenseRate = 0.0,
      postRiichiFoldRate = 0.0,
      shantenTrajectory = Vector.empty,
      calculatorVersion = CurrentCalculatorVersion,
      strictRoundSampleSize = 0,
      exactUkeireSampleRate = 0.0,
      exactDefenseSampleRate = 0.0,
      lastUpdatedAt = at
    )

enum AdvancedStatsRecomputeTaskStatus derives CanEqual:
  case Pending
  case Processing
  case Completed
  case Failed
  case DeadLetter

enum AdvancedStatsBackfillMode derives CanEqual:
  case Full
  case Missing
  case Stale

final case class AdvancedStatsRecomputeTask(
    id: AdvancedStatsRecomputeTaskId,
    owner: DashboardOwner,
    reason: String,
    calculatorVersion: Int,
    requestedAt: Instant,
    status: AdvancedStatsRecomputeTaskStatus,
    attempts: Int = 0,
    lastError: Option[String] = None,
    lastMatchRecordId: Option[MatchRecordId] = None,
    nextAttemptAt: Option[Instant] = None,
    startedAt: Option[Instant] = None,
    completedAt: Option[Instant] = None,
    deadLetteredAt: Option[Instant] = None,
    version: Int = 0
) derives CanEqual:
  require(reason.trim.nonEmpty, "Advanced stats recompute reason cannot be empty")
  require(attempts >= 0, "Advanced stats recompute attempts cannot be negative")

  def isRunnable(asOf: Instant): Boolean =
    status == AdvancedStatsRecomputeTaskStatus.Pending &&
      nextAttemptAt.forall(!_.isAfter(asOf))

  def markProcessing(at: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.Processing,
      attempts = attempts + 1,
      nextAttemptAt = None,
      startedAt = Some(at),
      completedAt = None,
      deadLetteredAt = None,
      lastError = None
    )

  def markCompleted(at: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.Completed,
      nextAttemptAt = None,
      completedAt = Some(at),
      deadLetteredAt = None,
      lastError = None
    )

  def markRetryScheduled(error: String, failedAt: Instant, retryAt: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.Pending,
      nextAttemptAt = Some(retryAt),
      completedAt = Some(failedAt),
      deadLetteredAt = None,
      lastError = Some(error)
    )

  def markFailed(error: String, at: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.Failed,
      nextAttemptAt = None,
      completedAt = Some(at),
      deadLetteredAt = None,
      lastError = Some(error)
    )

  def markDeadLetter(error: String, at: Instant): AdvancedStatsRecomputeTask =
    copy(
      status = AdvancedStatsRecomputeTaskStatus.DeadLetter,
      nextAttemptAt = None,
      completedAt = Some(at),
      deadLetteredAt = Some(at),
      lastError = Some(error)
    )

object AdvancedStatsRecomputeTask:
  def create(
      owner: DashboardOwner,
      reason: String,
      requestedAt: Instant = Instant.now(),
      calculatorVersion: Int = AdvancedStatsBoard.CurrentCalculatorVersion,
      lastMatchRecordId: Option[MatchRecordId] = None
  ): AdvancedStatsRecomputeTask =
    AdvancedStatsRecomputeTask(
      id = IdGenerator.advancedStatsRecomputeTaskId(),
      owner = owner,
      reason = reason,
      calculatorVersion = calculatorVersion,
      requestedAt = requestedAt,
      status = AdvancedStatsRecomputeTaskStatus.Pending,
      lastMatchRecordId = lastMatchRecordId
    )

final case class AdvancedStatsTaskQueueSummary(
    asOf: Instant,
    runnablePendingCount: Int,
    scheduledRetryCount: Int,
    processingCount: Int,
    completedCount: Int,
    failedCount: Int,
    deadLetterCount: Int,
    oldestRunnableRequestedAt: Option[Instant],
    nextScheduledRetryAt: Option[Instant],
    newestCompletedAt: Option[Instant]
) derives CanEqual

final case class PublicScheduleView(
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
) derives CanEqual

final case class PublicClubDirectoryEntry(
    clubId: ClubId,
    name: String,
    memberCount: Int,
    activeMemberCount: Int,
    adminCount: Int,
    powerRating: Double,
    totalPoints: Int,
    treasuryBalance: Long,
    pointPool: Int,
    allianceCount: Int,
    rivalryCount: Int,
    strongestRivalClubId: Option[ClubId],
    strongestRivalPower: Option[Double],
    honorTitles: Vector[String],
    relations: Vector[ClubRelation]
) derives CanEqual

final case class PlayerLeaderboardEntry(
    playerId: PlayerId,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshot,
    normalizedRankScore: Option[Int],
    clubIds: Vector[ClubId],
    status: PlayerStatus
) derives CanEqual

final case class ClubLeaderboardEntry(
    clubId: ClubId,
    name: String,
    powerRating: Double,
    totalPoints: Int,
    memberCount: Int
) derives CanEqual

enum SessionPrincipalKind derives CanEqual:
  case Anonymous
  case Guest
  case RegisteredPlayer

final case class CurrentSessionRoleFlags(
    isGuest: Boolean,
    isRegisteredPlayer: Boolean,
    isClubAdmin: Boolean,
    isTournamentAdmin: Boolean,
    isSuperAdmin: Boolean
) derives CanEqual

final case class CurrentSessionView(
    principalKind: SessionPrincipalKind,
    principalId: String,
    displayName: String,
    authenticated: Boolean,
    roles: CurrentSessionRoleFlags,
    player: Option[Player] = None,
    guestSession: Option[GuestAccessSession] = None
) derives CanEqual

final case class ClubMembershipApplicantView(
    playerId: Option[PlayerId],
    applicantUserId: Option[String],
    displayName: String,
    playerStatus: Option[PlayerStatus],
    currentRank: Option[RankSnapshot],
    elo: Option[Int],
    clubIds: Vector[ClubId]
) derives CanEqual

final case class ClubMembershipApplicationView(
    applicationId: MembershipApplicationId,
    clubId: ClubId,
    clubName: String,
    applicant: ClubMembershipApplicantView,
    submittedAt: Instant,
    message: Option[String],
    status: ClubMembershipApplicationStatus,
    reviewedBy: Option[PlayerId],
    reviewedByDisplayName: Option[String],
    reviewedAt: Option[Instant],
    reviewNote: Option[String],
    withdrawnByPrincipalId: Option[String],
    canReview: Boolean,
    canWithdraw: Boolean
) derives CanEqual

final case class ClubApplicationPolicyView(
    applicationsOpen: Boolean,
    requirementsText: Option[String],
    expectedReviewSlaHours: Option[Int],
    pendingApplicationCount: Int
) derives CanEqual

final case class PublicClubLineupMemberView(
    playerId: PlayerId,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshot,
    status: PlayerStatus,
    isAdmin: Boolean,
    internalTitle: Option[String],
    privileges: Vector[String]
) derives CanEqual

final case class PublicClubRecentMatchSeatView(
    playerId: PlayerId,
    nickname: String,
    clubId: Option[ClubId],
    seat: SeatWind,
    placement: Int,
    scoreDelta: Int,
    finalPoints: Int
) derives CanEqual

final case class PublicClubRecentMatchView(
    matchRecordId: MatchRecordId,
    tournamentId: TournamentId,
    tournamentName: String,
    stageId: TournamentStageId,
    stageName: String,
    tableId: TableId,
    generatedAt: Instant,
    seats: Vector[PublicClubRecentMatchSeatView]
) derives CanEqual

final case class PublicClubDetailView(
    clubId: ClubId,
    name: String,
    memberCount: Int,
    activeMemberCount: Int,
    adminCount: Int,
    powerRating: Double,
    totalPoints: Int,
    treasuryBalance: Long,
    pointPool: Int,
    relations: Vector[ClubRelation],
    honors: Vector[ClubHonor],
    applicationPolicy: ClubApplicationPolicyView,
    currentLineup: Vector[PublicClubLineupMemberView],
    recentMatches: Vector[PublicClubRecentMatchView]
) derives CanEqual

final case class TournamentStageDirectoryEntry(
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
) derives CanEqual

final case class PublicTournamentSummaryView(
    tournamentId: TournamentId,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: Instant,
    endsAt: Instant,
    stageCount: Int,
    activeStageCount: Int,
    participantCount: Int,
    clubCount: Int,
    playerCount: Int
) derives CanEqual

final case class PublicTournamentStageView(
    stageId: TournamentStageId,
    name: String,
    format: StageFormat,
    order: Int,
    status: StageStatus,
    currentRound: Int,
    roundCount: Int,
    tableCount: Int,
    archivedTableCount: Int,
    pendingTablePlanCount: Int,
    standings: Option[StageRankingSnapshot],
    bracket: Option[KnockoutBracketSnapshot]
) derives CanEqual

final case class PublicTournamentDetailView(
    tournamentId: TournamentId,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: Instant,
    endsAt: Instant,
    clubIds: Vector[ClubId],
    playerIds: Vector[PlayerId],
    whitelistCount: Int,
    stages: Vector[PublicTournamentStageView]
) derives CanEqual

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
