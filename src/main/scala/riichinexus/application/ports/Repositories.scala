package riichinexus.application.ports

import riichinexus.domain.model.*

trait PlayerRepository:
  def save(player: Player): Player
  def findById(id: PlayerId): Option[Player]
  def findByUserId(userId: String): Option[Player]
  def findAll(): Vector[Player]

  def findByClub(clubId: ClubId): Vector[Player] =
    findAll().filter(_.boundClubIds.contains(clubId))

trait AccountCredentialRepository:
  def save(credential: AccountCredential): AccountCredential
  def findByUsername(username: String): Option[AccountCredential]
  def findByPlayerId(playerId: PlayerId): Option[AccountCredential]
  def findAll(): Vector[AccountCredential]

trait AuthenticatedSessionRepository:
  def save(session: AuthenticatedSession): AuthenticatedSession
  def findByToken(token: String): Option[AuthenticatedSession]
  def findAll(): Vector[AuthenticatedSession]

trait GuestSessionRepository:
  def save(session: GuestAccessSession): GuestAccessSession
  def findById(id: GuestSessionId): Option[GuestAccessSession]
  def findAll(): Vector[GuestAccessSession]

trait ClubRepository:
  def save(club: Club): Club
  def findById(id: ClubId): Option[Club]
  def findByName(name: String): Option[Club]
  def findAll(): Vector[Club]

  def findActive(): Vector[Club] =
    findAll().filter(_.dissolvedAt.isEmpty)

trait TournamentRepository:
  def save(tournament: Tournament): Tournament
  def findById(id: TournamentId): Option[Tournament]
  def findByNameAndOrganizer(name: String, organizer: String): Option[Tournament]
  def findAll(): Vector[Tournament]

  def findByAdmin(playerId: PlayerId): Vector[Tournament] =
    findAll().filter(_.admins.contains(playerId))

trait TableRepository:
  def save(table: Table): Table
  def delete(id: TableId): Unit
  def findById(id: TableId): Option[Table]
  def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table]
  def findAll(): Vector[Table]

  def findByStatus(status: TableStatus): Vector[Table] =
    findAll().filter(_.status == status)

trait MatchRecordRepository:
  def save(record: MatchRecord): MatchRecord
  def findById(id: MatchRecordId): Option[MatchRecord]
  def findByTable(tableId: TableId): Option[MatchRecord]
  def findAll(): Vector[MatchRecord]

  def findByPlayer(playerId: PlayerId): Vector[MatchRecord] =
    findAll().filter(_.playerIds.contains(playerId))

trait PaifuRepository:
  def save(paifu: Paifu): Paifu
  def findById(id: PaifuId): Option[Paifu]
  def findAll(): Vector[Paifu]

  def findByPlayer(playerId: PlayerId): Vector[Paifu] =
    findAll().filter(_.playerIds.contains(playerId))

trait AppealTicketRepository:
  def save(ticket: AppealTicket): AppealTicket
  def findById(id: AppealTicketId): Option[AppealTicket]
  def findAll(): Vector[AppealTicket]

  def findByTournament(tournamentId: TournamentId): Vector[AppealTicket] =
    findAll().filter(_.tournamentId == tournamentId)

trait DashboardRepository:
  def save(dashboard: Dashboard): Dashboard
  def findByOwner(owner: DashboardOwner): Option[Dashboard]
  def findAll(): Vector[Dashboard]

trait AdvancedStatsBoardRepository:
  def save(board: AdvancedStatsBoard): AdvancedStatsBoard
  def findByOwner(owner: DashboardOwner): Option[AdvancedStatsBoard]
  def findAll(): Vector[AdvancedStatsBoard]

trait AdvancedStatsRecomputeTaskRepository:
  def save(task: AdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask
  def findById(id: AdvancedStatsRecomputeTaskId): Option[AdvancedStatsRecomputeTask]
  def findAll(): Vector[AdvancedStatsRecomputeTask]
  def findPending(limit: Int, asOf: java.time.Instant = java.time.Instant.now()): Vector[AdvancedStatsRecomputeTask]
  def findActiveByOwner(
      owner: DashboardOwner,
      calculatorVersion: Int
  ): Option[AdvancedStatsRecomputeTask]

trait GlobalDictionaryRepository:
  def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry
  def findByKey(key: String): Option[GlobalDictionaryEntry]
  def findAll(): Vector[GlobalDictionaryEntry]

trait DictionaryNamespaceRepository:
  def save(registration: DictionaryNamespaceRegistration): DictionaryNamespaceRegistration
  def findByPrefix(prefix: String): Option[DictionaryNamespaceRegistration]
  def findAll(): Vector[DictionaryNamespaceRegistration]

trait TournamentSettlementRepository:
  def save(snapshot: TournamentSettlementSnapshot): TournamentSettlementSnapshot
  def findById(id: SettlementSnapshotId): Option[TournamentSettlementSnapshot]
  def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot]
  def findByTournament(tournamentId: TournamentId): Vector[TournamentSettlementSnapshot]
  def findAll(): Vector[TournamentSettlementSnapshot]

trait EventCascadeRecordRepository:
  def save(record: EventCascadeRecord): EventCascadeRecord
  def findById(id: EventCascadeRecordId): Option[EventCascadeRecord]
  def findAll(): Vector[EventCascadeRecord]

  def findPending(limit: Int): Vector[EventCascadeRecord] =
    findAll()
      .filter(_.status == EventCascadeStatus.Pending)
      .sortBy(_.occurredAt)
      .take(limit)

  def findByAggregate(aggregateType: String, aggregateId: String): Vector[EventCascadeRecord] =
    findAll().filter(record => record.aggregateType == aggregateType && record.aggregateId == aggregateId)

trait DomainEventOutboxRepository:
  def save(record: DomainEventOutboxRecord): DomainEventOutboxRecord
  def findById(id: DomainEventOutboxRecordId): Option[DomainEventOutboxRecord]
  def findAll(): Vector[DomainEventOutboxRecord]

  def findPending(limit: Int, asOf: java.time.Instant = java.time.Instant.now()): Vector[DomainEventOutboxRecord] =
    findAll()
      .filter(_.isRunnable(asOf))
      .sortBy(_.sequenceNo)
      .take(limit)

trait DomainEventDeliveryReceiptRepository:
  def save(receipt: DomainEventDeliveryReceipt): DomainEventDeliveryReceipt
  def findById(id: DomainEventDeliveryReceiptId): Option[DomainEventDeliveryReceipt]
  def findAll(): Vector[DomainEventDeliveryReceipt]

  def findByOutboxRecordAndSubscriber(
      outboxRecordId: DomainEventOutboxRecordId,
      subscriberId: String
  ): Option[DomainEventDeliveryReceipt] =
    findAll().find(receipt =>
      receipt.outboxRecordId == outboxRecordId && receipt.subscriberId == subscriberId
    )

trait DomainEventSubscriberCursorRepository:
  def save(cursor: DomainEventSubscriberCursor): DomainEventSubscriberCursor
  def findById(id: DomainEventSubscriberCursorId): Option[DomainEventSubscriberCursor]
  def findAll(): Vector[DomainEventSubscriberCursor]

  def findBySubscriberAndPartition(
      subscriberId: String,
      partitionKey: String
  ): Option[DomainEventSubscriberCursor] =
    findAll().find(cursor =>
      cursor.subscriberId == subscriberId && cursor.partitionKey == partitionKey
    )

trait AuditEventRepository:
  def save(entry: AuditEventEntry): AuditEventEntry
  def findByAggregate(aggregateType: String, aggregateId: String): Vector[AuditEventEntry]
  def findAll(): Vector[AuditEventEntry]
