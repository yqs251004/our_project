package riichinexus

import riichinexus.bootstrap.ApplicationContext
import riichinexus.bootstrap.ApplicationContextTestAccess

trait TestApplicationAccess:
  private def repositories(app: ApplicationContext) =
    ApplicationContextTestAccess.repositories(app)

  protected def playerService(app: ApplicationContext) =
    app.playerModule.service

  protected def authService(app: ApplicationContext) =
    app.authModule.authService

  protected def guestSessionService(app: ApplicationContext) =
    app.authModule.guestSessionService

  protected def publicQueryService(app: ApplicationContext) =
    app.publicQueryModule.service

  protected def clubService(app: ApplicationContext) =
    app.clubModule.service

  protected def clubTables(app: ApplicationContext) =
    app.clubModule.tables

  protected def tournamentService(app: ApplicationContext) =
    app.tournamentModule.service

  protected def tournamentStageQueries(app: ApplicationContext) =
    app.tournamentModule.stageQueries

  protected def tableService(app: ApplicationContext) =
    app.tournamentModule.tableService

  protected def appealService(app: ApplicationContext) =
    app.tournamentAppealModule.service

  protected def superAdminService(app: ApplicationContext) =
    app.platformAdminModule.service

  protected def advancedStatsPipelineService(app: ApplicationContext) =
    app.opsAnalyticsModule.advancedStatsService

  protected def demoScenarioService(app: ApplicationContext) =
    app.opsAnalyticsModule.demoScenarioService

  protected def domainEventOperationsService(app: ApplicationContext) =
    app.opsAnalyticsModule.domainEventService

  protected def performanceDiagnosticsService(app: ApplicationContext) =
    app.opsAnalyticsModule.performanceDiagnosticsService

  protected def dictionaryGovernanceService(app: ApplicationContext) =
    app.dictionaryModule.governance

  protected def playerRepository(app: ApplicationContext) =
    repositories(app).playerRepository

  protected def clubRepository(app: ApplicationContext) =
    repositories(app).clubRepository

  protected def tournamentRepository(app: ApplicationContext) =
    repositories(app).tournamentRepository

  protected def tableRepository(app: ApplicationContext) =
    repositories(app).tableRepository

  protected def matchRecordRepository(app: ApplicationContext) =
    repositories(app).matchRecordRepository

  protected def advancedStatsBoardRepository(app: ApplicationContext) =
    repositories(app).advancedStatsBoardRepository

  protected def advancedStatsRecomputeTaskRepository(app: ApplicationContext) =
    repositories(app).advancedStatsRecomputeTaskRepository

  protected def dashboardRepository(app: ApplicationContext) =
    repositories(app).dashboardRepository

  protected def globalDictionaryRepository(app: ApplicationContext) =
    repositories(app).globalDictionaryRepository

  protected def tournamentSettlementRepository(app: ApplicationContext) =
    repositories(app).tournamentSettlementRepository

  protected def eventCascadeRecordRepository(app: ApplicationContext) =
    repositories(app).eventCascadeRecordRepository

  protected def auditEventRepository(app: ApplicationContext) =
    repositories(app).auditEventRepository
