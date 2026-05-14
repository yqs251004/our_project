package riichinexus.microservices.opsanalytics.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.auth.api.GuestSessionApplicationService
import riichinexus.microservices.club.api.ClubApplicationService
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport
import riichinexus.microservices.opsanalytics.api.responses.*
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.tournament.api.{TableLifecycleService, TournamentApplicationService}
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService

private[opsanalytics] final case class DemoScenarioConfig(
    variant: DemoScenarioVariant,
    tournamentName: String,
    organizer: String,
    stageId: TournamentStageId,
    stageName: String,
    guestDisplayName: String
)

private[opsanalytics] final case class DemoPlayerSeed(
    userId: String,
    nickname: String,
    rank: RankSnapshot,
    initialElo: Int
)

private[opsanalytics] trait DemoScenarioSupport:
  protected def playerService: PlayerApplicationService
  protected def guestSessionService: GuestSessionApplicationService
  protected def publicQueryService: PublicQueryService
  protected def clubService: ClubApplicationService
  protected def tournamentService: TournamentApplicationService
  protected def tableService: TableLifecycleService
  protected def appealService: AppealApplicationService
  protected def dashboardRepository: DashboardRepository
  protected def advancedStatsBoardRepository: AdvancedStatsBoardRepository
  protected def advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository
  protected def advancedStatsPipelineService: AdvancedStatsPipelineService
  protected def domainEventOutboxRepository: DomainEventOutboxRepository
  protected def appealTicketRepository: AppealTicketRepository
  protected def eventBus: DomainEventBus
  protected def playerRepository: PlayerRepository
  protected def guestSessionRepository: GuestSessionRepository
  protected def clubRepository: ClubRepository
  protected def tournamentRepository: TournamentRepository
  protected def tableRepository: TableRepository
  protected def matchRecordRepository: MatchRecordRepository

  protected val SeededAt = Instant.parse("2026-03-13T09:00:00Z")
  protected val DemoClubNames = Set("EastWind Club", "SouthWind Club")
  protected val DemoDerivedFlushPassLimit = 8

  protected val PlayerSeeds = Vector(
    DemoPlayerSeed("demo-alice", "Alice", RankSnapshot(RankPlatform.Tenhou, "4-dan"), 1610),
    DemoPlayerSeed("demo-bob", "Bob", RankSnapshot(RankPlatform.MahjongSoul, "Expert-3"), 1540),
    DemoPlayerSeed("demo-charlie", "Charlie", RankSnapshot(RankPlatform.Tenhou, "3-dan"), 1490),
    DemoPlayerSeed("demo-diana", "Diana", RankSnapshot(RankPlatform.MahjongSoul, "Master-1"), 1580),
    DemoPlayerSeed("demo-eve", "Eve", RankSnapshot(RankPlatform.Tenhou, "5-dan"), 1650),
    DemoPlayerSeed("demo-frank", "Frank", RankSnapshot(RankPlatform.MahjongSoul, "Adept-2"), 1470),
    DemoPlayerSeed("demo-grace", "Grace", RankSnapshot(RankPlatform.Tenhou, "2-dan"), 1450),
    DemoPlayerSeed("demo-heidi", "Heidi", RankSnapshot(RankPlatform.MahjongSoul, "Master-2"), 1600)
  )

  protected def buildScenarioSnapshot(
      config: DemoScenarioConfig,
      recommendedOperatorId: PlayerId,
      tournament: Tournament,
      stage: TournamentStage
  ): DemoScenarioSnapshot

  protected def buildActionCatalog(snapshot: DemoScenarioSnapshot): Vector[DemoScenarioActionSpec]
  protected def demoPaifu(table: Table, tournamentId: TournamentId, stageId: TournamentStageId, endedAt: Instant): Paifu
  protected def principalFor(playerId: PlayerId): AccessPrincipal

  protected def scenarioConfig(
      variant: DemoScenarioVariant
  ): DemoScenarioConfig =
    variant match
      case DemoScenarioVariant.Basic =>
        DemoScenarioConfig(
          variant = variant,
          tournamentName = "RiichiNexus Spring Demo",
          organizer = "Frontend Demo",
          stageId = TournamentStageId("stage-demo-swiss"),
          stageName = "Swiss Stage 1",
          guestDisplayName = "demo-guest-basic"
        )
      case DemoScenarioVariant.Leaderboard =>
        DemoScenarioConfig(
          variant = variant,
          tournamentName = "RiichiNexus Leaderboard Demo",
          organizer = "Frontend Demo",
          stageId = TournamentStageId("stage-demo-leaderboard"),
          stageName = "Swiss Stage Leaderboard",
          guestDisplayName = "demo-guest-leaderboard"
        )
      case DemoScenarioVariant.Appeal =>
        DemoScenarioConfig(
          variant = variant,
          tournamentName = "RiichiNexus Appeal Demo",
          organizer = "Frontend Demo",
          stageId = TournamentStageId("stage-demo-appeal"),
          stageName = "Swiss Stage Appeal",
          guestDisplayName = "demo-guest-appeal"
        )

  protected def toDemoDashboardSummary(dashboard: Dashboard): DemoScenarioDashboardSummary =
    DemoScenarioDashboardSummary(
      sampleSize = dashboard.sampleSize,
      dealInRate = dashboard.dealInRate,
      winRate = dashboard.winRate,
      averageWinPoints = dashboard.averageWinPoints,
      riichiRate = dashboard.riichiRate,
      averagePlacement = dashboard.averagePlacement,
      topFinishRate = dashboard.topFinishRate,
      lastUpdatedAt = dashboard.lastUpdatedAt
    )

  protected def toDemoAdvancedStatsSummary(board: AdvancedStatsBoard): DemoScenarioAdvancedStatsSummary =
    DemoScenarioAdvancedStatsSummary(
      sampleSize = board.sampleSize,
      defenseStability = board.defenseStability,
      ukeireExpectation = board.ukeireExpectation,
      averageShantenImprovement = board.averageShantenImprovement,
      callAggressionRate = board.callAggressionRate,
      riichiConversionRate = board.riichiConversionRate,
      pressureDefenseRate = board.pressureDefenseRate,
      postRiichiFoldRate = board.postRiichiFoldRate,
      lastUpdatedAt = board.lastUpdatedAt
    )

  protected def flushDerivedViews(): Unit =
    var pass = 0
    var keepWorking = true

    while keepWorking && pass < DemoDerivedFlushPassLimit do
      pass += 1
      val now = Instant.now()
      val processedEvents = eventBus.drainPendingNow(limit = 200, processedAt = now)
      val processedAdvancedStats =
        advancedStatsPipelineService.processPending(limit = 200, processedAt = now).size
      keepWorking = processedEvents > 0 || processedAdvancedStats > 0
