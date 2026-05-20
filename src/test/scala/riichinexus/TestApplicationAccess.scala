package riichinexus

import riichinexus.bootstrap.ApplicationContext
import riichinexus.bootstrap.ApplicationContextTestAccess
import riichinexus.bootstrap.ApplicationRepositoryContext
import riichinexus.bootstrap.instrumentation.PerformanceDiagnosticsService
import riichinexus.api.ApiPlanContext
import riichinexus.api.http.{RouteContext, RouteSupport}
import riichinexus.application.ports.*
import riichinexus.domain.model.GuestAccessSession
import riichinexus.microservices.auth.api.CreateGuestSessionAuthAPIMessage
import riichinexus.microservices.club.tables.ClubTables
import riichinexus.microservices.club.objects.ClubTestClient
import riichinexus.microservices.dictionary.objects.DictionaryTestClient
import riichinexus.microservices.player.domain.PlayerRegistrationOperations
import riichinexus.microservices.publicquery.tables.PublicQueryTables
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.tournament.domain.TournamentStageQueryService
import riichinexus.microservices.tournament.objects.TournamentAPIMessageTestClient
import riichinexus.microservices.tournament.objects.TournamentTableAPIMessageTestClient
import cats.effect.unsafe.implicits.global

trait TestApplicationAccess:
  private def repositories(app: ApplicationContext): ApplicationRepositoryContext =
    ApplicationContextTestAccess.repositories(app)

  protected def playerService(app: ApplicationContext): PlayerRegistrationOperations =
    app.playerModule.registration

  protected def createGuestSession(
      app: ApplicationContext,
      displayName: String
  ): GuestAccessSession =
    CreateGuestSessionAuthAPIMessage(displayName = Some(displayName))
      .plan(apiPlanContext(app))
      .unsafeRunSync()

  protected def publicQueryOperations(app: ApplicationContext): PublicQueryTables =
    app.publicQueryModule.tables

  protected def clubApi(app: ApplicationContext): ClubTestClient =
    ClubTestClient.from(app.clubModule)

  protected def clubTables(app: ApplicationContext): ClubTables =
    app.clubModule.tables

  protected def tournamentService(app: ApplicationContext): TournamentAPIMessageTestClient =
    TournamentAPIMessageTestClient(app, apiPlanContext(app))

  protected def tournamentStageQueries(app: ApplicationContext): TournamentStageQueryService =
    app.tournamentModule.stageQueries

  protected def tableService(app: ApplicationContext): TournamentTableAPIMessageTestClient =
    TournamentTableAPIMessageTestClient(app, apiPlanContext(app))

  protected def appealService(app: ApplicationContext): AppealApplicationService =
    app.tournamentAppealModule.service

  protected def performanceDiagnosticsService(app: ApplicationContext): PerformanceDiagnosticsService =
    app.opsAnalyticsModule.performanceDiagnosticsService

  protected def apiPlanContext(app: ApplicationContext): ApiPlanContext =
    ApiPlanContext(
      RouteSupport(
        RouteContext(
          authModule = app.authModule,
          playerModule = app.playerModule,
          clubModule = app.clubModule,
          dictionaryModule = app.dictionaryModule,
          publicQueryModule = app.publicQueryModule,
          opsAnalyticsModule = app.opsAnalyticsModule,
          tournamentModule = app.tournamentModule,
          platformAdminModule = app.platformAdminModule,
          tournamentAppealModule = app.tournamentAppealModule,
          authorizationService = app.authorizationService,
          storageLabel = "memory",
          corsAllowOrigin = "*"
        )
      ),
      bearerToken = None
    )

  protected def dictionaryApiClient(app: ApplicationContext): DictionaryTestClient =
    DictionaryTestClient(app.dictionaryModule)

  protected def playerRepository(app: ApplicationContext): PlayerRepository =
    repositories(app).playerRepository

  protected def clubRepository(app: ApplicationContext): ClubRepository =
    repositories(app).clubRepository

  protected def tournamentRepository(app: ApplicationContext): TournamentRepository =
    repositories(app).tournamentRepository

  protected def tableRepository(app: ApplicationContext): TableRepository =
    repositories(app).tableRepository

  protected def matchRecordRepository(app: ApplicationContext): MatchRecordRepository =
    repositories(app).matchRecordRepository

  protected def advancedStatsBoardRepository(app: ApplicationContext): AdvancedStatsBoardRepository =
    repositories(app).advancedStatsBoardRepository

  protected def advancedStatsRecomputeTaskRepository(app: ApplicationContext): AdvancedStatsRecomputeTaskRepository =
    repositories(app).advancedStatsRecomputeTaskRepository

  protected def dashboardRepository(app: ApplicationContext): DashboardRepository =
    repositories(app).dashboardRepository

  protected def globalDictionaryRepository(app: ApplicationContext): GlobalDictionaryRepository =
    repositories(app).globalDictionaryRepository

  protected def tournamentSettlementRepository(app: ApplicationContext): TournamentSettlementRepository =
    repositories(app).tournamentSettlementRepository

  protected def eventCascadeRecordRepository(app: ApplicationContext): EventCascadeRecordRepository =
    repositories(app).eventCascadeRecordRepository

  protected def auditEventRepository(app: ApplicationContext): AuditEventRepository =
    repositories(app).auditEventRepository
