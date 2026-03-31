package riichinexus.infrastructure.memory

import scala.collection.mutable

import riichinexus.application.ports.*
import riichinexus.domain.event.DomainEvent
import riichinexus.domain.model.*

private object InMemoryOptimisticLockSupport:
  def nextVersion(
      aggregateType: String,
      aggregateId: String,
      incomingVersion: Int,
      currentVersion: Option[Int]
  ): Int =
    currentVersion match
      case None =>
        if incomingVersion != 0 then
          throw OptimisticConcurrencyException(aggregateType, aggregateId, incomingVersion, None)
        1
      case Some(actual) =>
        if actual != incomingVersion then
          throw OptimisticConcurrencyException(aggregateType, aggregateId, incomingVersion, Some(actual))
        actual + 1

final class InMemoryPlayerRepository extends PlayerRepository:
  private val state = mutable.LinkedHashMap.empty[PlayerId, Player]

  override def save(player: Player): Player =
    val persisted = player.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "player",
        player.id.value,
        player.version,
        state.get(player.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def findById(id: PlayerId): Option[Player] =
    state.get(id)

  override def findByUserId(userId: String): Option[Player] =
    state.values.find(_.userId == userId)

  override def findAll(): Vector[Player] =
    state.values.toVector

type InMemoryGuestSessionRepository = _root_.database.memory.InMemoryGuestSessionRepository

final class InMemoryClubRepository extends ClubRepository:
  private val state = mutable.LinkedHashMap.empty[ClubId, Club]

  override def save(club: Club): Club =
    val persisted = club.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "club",
        club.id.value,
        club.version,
        state.get(club.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def findById(id: ClubId): Option[Club] =
    state.get(id)

  override def findByName(name: String): Option[Club] =
    state.values.find(_.name == name)

  override def findAll(): Vector[Club] =
    state.values.toVector

final class InMemoryTournamentRepository extends TournamentRepository:
  private val state = mutable.LinkedHashMap.empty[TournamentId, Tournament]

  override def save(tournament: Tournament): Tournament =
    val persisted = tournament.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "tournament",
        tournament.id.value,
        tournament.version,
        state.get(tournament.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def findById(id: TournamentId): Option[Tournament] =
    state.get(id)

  override def findByNameAndOrganizer(name: String, organizer: String): Option[Tournament] =
    state.values.find(tournament =>
      tournament.name == name && tournament.organizer == organizer
    )

  override def findAll(): Vector[Tournament] =
    state.values.toVector

final class InMemoryTableRepository extends TableRepository:
  private val state = mutable.LinkedHashMap.empty[TableId, Table]

  override def save(table: Table): Table =
    val persisted = table.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "table",
        table.id.value,
        table.version,
        state.get(table.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def delete(id: TableId): Unit =
    state.remove(id)

  override def findById(id: TableId): Option[Table] =
    state.get(id)

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    state.values
      .filter(table => table.tournamentId == tournamentId && table.stageId == stageId)
      .toVector

  override def findAll(): Vector[Table] =
    state.values.toVector

type InMemoryMatchRecordRepository = _root_.database.memory.InMemoryMatchRecordRepository
type InMemoryPaifuRepository = _root_.database.memory.InMemoryPaifuRepository

final class InMemoryAppealTicketRepository extends AppealTicketRepository:
  private val state = mutable.LinkedHashMap.empty[AppealTicketId, AppealTicket]

  override def save(ticket: AppealTicket): AppealTicket =
    val persisted = ticket.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "appeal-ticket",
        ticket.id.value,
        ticket.version,
        state.get(ticket.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def findById(id: AppealTicketId): Option[AppealTicket] =
    state.get(id)

  override def findAll(): Vector[AppealTicket] =
    state.values.toVector

final class InMemoryDashboardRepository extends DashboardRepository:
  private val state = mutable.LinkedHashMap.empty[String, Dashboard]

  override def save(dashboard: Dashboard): Dashboard =
    val key = ownerKey(dashboard.owner)
    val persisted = dashboard.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "dashboard",
        key,
        dashboard.version,
        state.get(key).map(_.version)
      )
    )
    state.update(key, persisted)
    persisted

  override def findByOwner(owner: DashboardOwner): Option[Dashboard] =
    state.get(ownerKey(owner))

  override def findAll(): Vector[Dashboard] =
    state.values.toVector

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

final class InMemoryAdvancedStatsBoardRepository extends AdvancedStatsBoardRepository:
  private val state = mutable.LinkedHashMap.empty[String, AdvancedStatsBoard]

  override def save(board: AdvancedStatsBoard): AdvancedStatsBoard =
    val key = ownerKey(board.owner)
    val persisted = board.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "advanced-stats-board",
        key,
        board.version,
        state.get(key).map(_.version)
      )
    )
    state.update(key, persisted)
    persisted

  override def findByOwner(owner: DashboardOwner): Option[AdvancedStatsBoard] =
    state.get(ownerKey(owner))

  override def findAll(): Vector[AdvancedStatsBoard] =
    state.values.toVector

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

final class InMemoryAdvancedStatsRecomputeTaskRepository extends AdvancedStatsRecomputeTaskRepository:
  private val state = mutable.LinkedHashMap.empty[AdvancedStatsRecomputeTaskId, AdvancedStatsRecomputeTask]

  override def save(task: AdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask =
    val persisted = task.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "advanced-stats-task",
        task.id.value,
        task.version,
        state.get(task.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def findById(id: AdvancedStatsRecomputeTaskId): Option[AdvancedStatsRecomputeTask] =
    state.get(id)

  override def findAll(): Vector[AdvancedStatsRecomputeTask] =
    state.values.toVector.sortBy(_.requestedAt)

  override def findPending(limit: Int, asOf: java.time.Instant = java.time.Instant.now()): Vector[AdvancedStatsRecomputeTask] =
    state.values
      .filter(_.isRunnable(asOf))
      .toVector
      .sortBy(_.requestedAt)
      .take(limit)

  override def findActiveByOwner(
      owner: DashboardOwner,
      calculatorVersion: Int
  ): Option[AdvancedStatsRecomputeTask] =
    state.values.find { task =>
      task.owner == owner &&
      task.calculatorVersion == calculatorVersion &&
      (task.status == AdvancedStatsRecomputeTaskStatus.Pending ||
        task.status == AdvancedStatsRecomputeTaskStatus.Processing)
    }

final class InMemoryGlobalDictionaryRepository extends GlobalDictionaryRepository:
  private val state = mutable.LinkedHashMap.empty[String, GlobalDictionaryEntry]

  override def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry =
    val persisted = entry.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "global-dictionary-entry",
        entry.key,
        entry.version,
        state.get(entry.key).map(_.version)
      )
    )
    state.update(persisted.key, persisted)
    persisted

  override def findByKey(key: String): Option[GlobalDictionaryEntry] =
    state.get(key)

  override def findAll(): Vector[GlobalDictionaryEntry] =
    state.values.toVector

final class InMemoryDictionaryNamespaceRepository extends DictionaryNamespaceRepository:
  private val state = mutable.LinkedHashMap.empty[String, DictionaryNamespaceRegistration]

  override def save(registration: DictionaryNamespaceRegistration): DictionaryNamespaceRegistration =
    val persisted = registration.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "dictionary-namespace",
        registration.namespacePrefix,
        registration.version,
        state.get(registration.namespacePrefix).map(_.version)
      )
    )
    state.update(persisted.namespacePrefix, persisted)
    persisted

  override def findByPrefix(prefix: String): Option[DictionaryNamespaceRegistration] =
    state.get(prefix)

  override def findAll(): Vector[DictionaryNamespaceRegistration] =
    state.values.toVector.sortBy(_.namespacePrefix)

final class InMemoryTournamentSettlementRepository extends TournamentSettlementRepository:
  private val state = mutable.LinkedHashMap.empty[SettlementSnapshotId, TournamentSettlementSnapshot]

  override def save(snapshot: TournamentSettlementSnapshot): TournamentSettlementSnapshot =
    val persisted = snapshot.copy(
      version = InMemoryOptimisticLockSupport.nextVersion(
        "tournament-settlement",
        snapshot.id.value,
        snapshot.version,
        state.get(snapshot.id).map(_.version)
      )
    )
    state.update(persisted.id, persisted)
    persisted

  override def findById(id: SettlementSnapshotId): Option[TournamentSettlementSnapshot] =
    state.get(id)

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    state.values
      .filter(snapshot => snapshot.tournamentId == tournamentId && snapshot.stageId == stageId)
      .toVector
      .sortBy(snapshot => (snapshot.revision, snapshot.generatedAt))
      .lastOption

  override def findByTournament(tournamentId: TournamentId): Vector[TournamentSettlementSnapshot] =
    state.values.filter(_.tournamentId == tournamentId).toVector.sortBy(snapshot => (snapshot.generatedAt, snapshot.revision))

  override def findAll(): Vector[TournamentSettlementSnapshot] =
    state.values.toVector.sortBy(snapshot => (snapshot.generatedAt, snapshot.revision))

type InMemoryEventCascadeRecordRepository = _root_.database.memory.InMemoryEventCascadeRecordRepository

type InMemoryDomainEventOutboxRepository = _root_.database.memory.InMemoryDomainEventOutboxRepository
type InMemoryDomainEventDeliveryReceiptRepository = _root_.database.memory.InMemoryDomainEventDeliveryReceiptRepository
type InMemoryDomainEventSubscriberCursorRepository = _root_.database.memory.InMemoryDomainEventSubscriberCursorRepository
type InMemoryAuditEventRepository = _root_.database.memory.InMemoryAuditEventRepository

type InMemoryDomainEventBus = _root_.database.memory.InMemoryDomainEventBus
