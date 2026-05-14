package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport

final class TournamentApplicationService(
    protected val tournamentRepository: TournamentRepository,
    protected val playerRepository: PlayerRepository,
    protected val clubRepository: ClubRepository,
    protected val globalDictionaryRepository: GlobalDictionaryRepository,
    protected val tableRepository: TableRepository,
    protected val matchRecordRepository: MatchRecordRepository,
    protected val tournamentSettlementRepository: TournamentSettlementRepository,
    protected val auditEventRepository: AuditEventRepository,
    protected val seatingPolicy: SeatingPolicy,
    protected val tournamentRuleEngine: TournamentRuleEngine,
    protected val knockoutStageCoordinator: KnockoutStageCoordinator,
    protected val stageQueries: TournamentStageQueryService,
    protected val eventBus: DomainEventBus,
    protected val transactionManager: TransactionManager = NoOpTransactionManager,
    protected val authorizationService: AuthorizationService = NoOpAuthorizationService
) extends TournamentWorkflowSupport
    with TournamentManagementWorkflow
    with TournamentStageWorkflow
    with TournamentSettlementWorkflow
