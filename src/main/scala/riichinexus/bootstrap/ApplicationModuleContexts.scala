package riichinexus.bootstrap

import riichinexus.application.ports.*
import riichinexus.bootstrap.instrumentation.PerformanceDiagnosticsService
import riichinexus.microservices.player.domain.PlayerRegistrationOperations
import riichinexus.microservices.tournament.domain.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.TournamentStageQueryService
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.domain.service.*

final case class AuthModuleContext(
    playerRegistration: PlayerRegistrationOperations,
    clubRepository: ClubRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
)

final case class PlayerModuleContext(
    registration: PlayerRegistrationOperations
)

final case class ClubModuleContext(
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dashboardRepository: DashboardRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService,
    tournamentModule: TournamentModuleContext
)

final case class DictionaryModuleContext(
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dictionaryNamespaceRepository: DictionaryNamespaceRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService
)

final case class PublicQueryModuleContext()

final case class TournamentModuleContext(
    tournamentRepository: TournamentRepository,
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
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
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
    clubRepository: ClubRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService
)

final case class TournamentAppealModuleContext(
    service: AppealApplicationService
)
