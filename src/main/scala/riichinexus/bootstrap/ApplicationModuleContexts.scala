package riichinexus.bootstrap

import riichinexus.application.ports.*
import riichinexus.bootstrap.instrumentation.PerformanceDiagnosticsService
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
import riichinexus.microservices.auth.tables.player.AuthPlayerTable
import riichinexus.microservices.club.tables.ClubTables
import riichinexus.microservices.dictionary.tables.DictionaryTables
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables
import riichinexus.microservices.platformadmin.tables.PlatformAdminTables
import riichinexus.microservices.player.domain.PlayerRegistrationOperations
import riichinexus.microservices.player.tables.PlayerTables
import riichinexus.microservices.publicquery.tables.PublicQueryTables
import riichinexus.microservices.tournament.domain.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.TournamentStageQueryService
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.tournament.appeal.tables.TournamentAppealTables
import riichinexus.microservices.tournament.tables.TournamentTables
import riichinexus.domain.service.*

final case class AuthModuleContext(
    playerTable: AuthPlayerTable,
    guestSessionTable: GuestSessionTable,
    playerRegistration: PlayerRegistrationOperations,
    playerRepository: PlayerRepository,
    accountCredentialRepository: AccountCredentialRepository,
    authenticatedSessionRepository: AuthenticatedSessionRepository,
    guestSessionRepository: GuestSessionRepository,
    clubRepository: ClubRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
)

final case class PlayerModuleContext(
    tables: PlayerTables,
    registration: PlayerRegistrationOperations
)

final case class ClubModuleContext(
    tables: ClubTables,
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dashboardRepository: DashboardRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService,
    tournamentModule: TournamentModuleContext
)

final case class DictionaryModuleContext(
    tables: DictionaryTables,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dictionaryNamespaceRepository: DictionaryNamespaceRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService
)

final case class PublicQueryModuleContext(
    tables: PublicQueryTables
)

final case class TournamentModuleContext(
    tables: TournamentTables,
    tournamentRepository: TournamentRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    tableRepository: TableRepository,
    matchRecordRepository: MatchRecordRepository,
    paifuRepository: PaifuRepository,
    tournamentSettlementRepository: TournamentSettlementRepository,
    auditEventRepository: AuditEventRepository,
    seatingPolicy: SeatingPolicy,
    tournamentRuleEngine: TournamentRuleEngine,
    knockoutStageCoordinator: KnockoutStageCoordinator,
    stageQueries: TournamentStageQueryService,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService
)

final case class OpsAnalyticsModuleContext(
    tables: OpsAnalyticsTables,
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
    domainEventOutboxRepository: DomainEventOutboxRepository,
    domainEventDeliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
    domainEventSubscriberCursorRepository: DomainEventSubscriberCursorRepository,
    domainEventSubscribers: Vector[DomainEventSubscriber],
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService,
    performanceDiagnosticsService: PerformanceDiagnosticsService
)

final case class PlatformAdminModuleContext(
    tables: PlatformAdminTables,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService
)

final case class TournamentAppealModuleContext(
    tables: TournamentAppealTables,
    service: AppealApplicationService
)
