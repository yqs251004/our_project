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
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
)

final case class PlayerModuleContext(
    registration: PlayerRegistrationOperations
)

final case class ClubModuleContext(
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService,
    tournamentModule: TournamentModuleContext
)

final case class PublicQueryModuleContext()

final case class TournamentModuleContext(
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
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService
)

final case class TournamentAppealModuleContext(
    service: AppealApplicationService
)
