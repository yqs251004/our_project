package riichinexus.bootstrap

import riichinexus.application.ports.*

final case class ApplicationRepositoryContext(
    playerRepository: PlayerRepository,
    accountCredentialRepository: AccountCredentialRepository,
    authenticatedSessionRepository: AuthenticatedSessionRepository,
    guestSessionRepository: GuestSessionRepository,
    clubRepository: ClubRepository,
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    matchRecordRepository: MatchRecordRepository,
    paifuRepository: PaifuRepository,
    appealTicketRepository: AppealTicketRepository,
    dashboardRepository: DashboardRepository,
    advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dictionaryNamespaceRepository: DictionaryNamespaceRepository,
    tournamentSettlementRepository: TournamentSettlementRepository,
    eventCascadeRecordRepository: EventCascadeRecordRepository,
    domainEventOutboxRepository: DomainEventOutboxRepository,
    domainEventDeliveryReceiptRepository: DomainEventDeliveryReceiptRepository,
    domainEventSubscriberCursorRepository: DomainEventSubscriberCursorRepository,
    auditEventRepository: AuditEventRepository
)
