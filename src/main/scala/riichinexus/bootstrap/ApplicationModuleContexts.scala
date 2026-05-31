package riichinexus.bootstrap

import riichinexus.application.ports.*
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.tournament.domain.paifumanagement.functions.TournamentPaifuArchiveService
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.TournamentSettlementCoordinator
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentStageCompletionCoordinator
import riichinexus.microservices.auth.domain.*

final case class AuthModuleContext(
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager
)

final case class PlayerModuleContext()

final case class ClubModuleContext(
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationPolicy,
    tournamentModule: TournamentModuleContext
)

final case class TournamentModuleContext(
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationPolicy,
    paifuArchiveService: TournamentPaifuArchiveService,
    settlementCoordinator: TournamentSettlementCoordinator,
    stageCompletionCoordinator: TournamentStageCompletionCoordinator
)

final case class OpsAnalyticsModuleContext(
    domainEventOutboxRepository: DomainEventOutboxRepository,
    domainEventDeliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
    domainEventSubscriberCursorRepository: DomainEventSubscriberCursorRepository,
    domainEventSubscribers: Vector[DomainEventSubscriber],
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationPolicy
)

final case class PlatformAdminModuleContext(
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationPolicy
)

final case class TournamentAppealModuleContext(
    service: AppealApplicationService
)
