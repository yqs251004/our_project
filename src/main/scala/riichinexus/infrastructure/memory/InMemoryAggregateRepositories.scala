package riichinexus.infrastructure.memory

import riichinexus.application.ports.*
import riichinexus.domain.model.*

private object InMemoryAggregateRepositoryLockSupport:
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
  private val state = InMemoryKeyValueStore[PlayerId, Player]()

  override def save(player: Player): Player =
    val persisted = player.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "player",
        player.id.value,
        player.version,
        state.get(player.id).map(_.version)
      )
    )
    state.upsert(persisted.id, persisted)
    persisted

  override def findById(id: PlayerId): Option[Player] =
    state.get(id)

  override def findByUserId(userId: String): Option[Player] =
    state.values.find(_.userId == userId)

  override def findAll(): Vector[Player] =
    state.values

object InMemoryPlayerRepository:
  def apply(): InMemoryPlayerRepository =
    new InMemoryPlayerRepository()

final class InMemoryClubRepository extends ClubRepository:
  private val state = InMemoryKeyValueStore[ClubId, Club]()

  override def save(club: Club): Club =
    val persisted = club.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "club",
        club.id.value,
        club.version,
        state.get(club.id).map(_.version)
      )
    )
    state.upsert(persisted.id, persisted)
    persisted

  override def findById(id: ClubId): Option[Club] =
    state.get(id)

  override def findByName(name: String): Option[Club] =
    state.values.find(_.name == name)

  override def findAll(): Vector[Club] =
    state.values

object InMemoryClubRepository:
  def apply(): InMemoryClubRepository =
    new InMemoryClubRepository()

final class InMemoryTournamentRepository extends TournamentRepository:
  private val state = InMemoryKeyValueStore[TournamentId, Tournament]()

  override def save(tournament: Tournament): Tournament =
    val normalized = TournamentDefaults.ensureInitialStage(tournament)
    val persisted = normalized.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "tournament",
        normalized.id.value,
        normalized.version,
        state.get(normalized.id).map(_.version)
      )
    )
    state.upsert(persisted.id, persisted)
    persisted

  override def findById(id: TournamentId): Option[Tournament] =
    state.get(id).map(normalizeOnRead)

  override def findByNameAndOrganizer(name: String, organizer: String): Option[Tournament] =
    state.values.find(tournament =>
      tournament.name == name && tournament.organizer == organizer
    ).map(normalizeOnRead)

  override def findAll(): Vector[Tournament] =
    state.values.map(normalizeOnRead)

  private def normalizeOnRead(tournament: Tournament): Tournament =
    if tournament.stages.nonEmpty then tournament
    else save(TournamentDefaults.ensureInitialStage(tournament))

object InMemoryTournamentRepository:
  def apply(): InMemoryTournamentRepository =
    new InMemoryTournamentRepository()

final class InMemoryTableRepository extends TableRepository:
  private val state = InMemoryKeyValueStore[TableId, Table]()

  override def save(table: Table): Table =
    val persisted = table.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "table",
        table.id.value,
        table.version,
        state.get(table.id).map(_.version)
      )
    )
    state.upsert(persisted.id, persisted)
    persisted

  override def delete(id: TableId): Unit =
    state.delete(id)

  override def findById(id: TableId): Option[Table] =
    state.get(id)

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    state.values
      .filter(table => table.tournamentId == tournamentId && table.stageId == stageId)

  override def findAll(): Vector[Table] =
    state.values

object InMemoryTableRepository:
  def apply(): InMemoryTableRepository =
    new InMemoryTableRepository()

final class InMemoryAppealTicketRepository extends AppealTicketRepository:
  private val state = InMemoryKeyValueStore[AppealTicketId, AppealTicket]()

  override def save(ticket: AppealTicket): AppealTicket =
    val persisted = ticket.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "appeal-ticket",
        ticket.id.value,
        ticket.version,
        state.get(ticket.id).map(_.version)
      )
    )
    state.upsert(persisted.id, persisted)
    persisted

  override def findById(id: AppealTicketId): Option[AppealTicket] =
    state.get(id)

  override def findAll(): Vector[AppealTicket] =
    state.values

object InMemoryAppealTicketRepository:
  def apply(): InMemoryAppealTicketRepository =
    new InMemoryAppealTicketRepository()

final class InMemoryDashboardRepository extends DashboardRepository:
  private val state = InMemoryKeyValueStore[String, Dashboard]()

  override def save(dashboard: Dashboard): Dashboard =
    val key = ownerKey(dashboard.owner)
    val persisted = dashboard.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "dashboard",
        key,
        dashboard.version,
        state.get(key).map(_.version)
      )
    )
    state.upsert(key, persisted)
    persisted

  override def findByOwner(owner: DashboardOwner): Option[Dashboard] =
    state.get(ownerKey(owner))

  override def findAll(): Vector[Dashboard] =
    state.values

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

object InMemoryDashboardRepository:
  def apply(): InMemoryDashboardRepository =
    new InMemoryDashboardRepository()

final class InMemoryAdvancedStatsBoardRepository extends AdvancedStatsBoardRepository:
  private val state = InMemoryKeyValueStore[String, AdvancedStatsBoard]()

  override def save(board: AdvancedStatsBoard): AdvancedStatsBoard =
    val key = ownerKey(board.owner)
    val persisted = board.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "advanced-stats-board",
        key,
        board.version,
        state.get(key).map(_.version)
      )
    )
    state.upsert(key, persisted)
    persisted

  override def findByOwner(owner: DashboardOwner): Option[AdvancedStatsBoard] =
    state.get(ownerKey(owner))

  override def findAll(): Vector[AdvancedStatsBoard] =
    state.values

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

object InMemoryAdvancedStatsBoardRepository:
  def apply(): InMemoryAdvancedStatsBoardRepository =
    new InMemoryAdvancedStatsBoardRepository()

final class InMemoryAdvancedStatsRecomputeTaskRepository extends AdvancedStatsRecomputeTaskRepository:
  private val state = InMemoryKeyValueStore[AdvancedStatsRecomputeTaskId, AdvancedStatsRecomputeTask]()

  override def save(task: AdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask =
    val persisted = task.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "advanced-stats-task",
        task.id.value,
        task.version,
        state.get(task.id).map(_.version)
      )
    )
    state.upsert(persisted.id, persisted)
    persisted

  override def findById(id: AdvancedStatsRecomputeTaskId): Option[AdvancedStatsRecomputeTask] =
    state.get(id)

  override def findAll(): Vector[AdvancedStatsRecomputeTask] =
    state.values.sortBy(_.requestedAt)

  override def findPending(limit: Int, asOf: java.time.Instant = java.time.Instant.now()): Vector[AdvancedStatsRecomputeTask] =
    state.values
      .filter(_.isRunnable(asOf))
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

object InMemoryAdvancedStatsRecomputeTaskRepository:
  def apply(): InMemoryAdvancedStatsRecomputeTaskRepository =
    new InMemoryAdvancedStatsRecomputeTaskRepository()

final class InMemoryGlobalDictionaryRepository extends GlobalDictionaryRepository:
  private val state = InMemoryKeyValueStore[String, GlobalDictionaryEntry]()

  override def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry =
    val persisted = entry.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "global-dictionary-entry",
        entry.key,
        entry.version,
        state.get(entry.key).map(_.version)
      )
    )
    state.upsert(persisted.key, persisted)
    persisted

  override def findByKey(key: String): Option[GlobalDictionaryEntry] =
    state.get(key)

  override def findAll(): Vector[GlobalDictionaryEntry] =
    state.values

object InMemoryGlobalDictionaryRepository:
  def apply(): InMemoryGlobalDictionaryRepository =
    new InMemoryGlobalDictionaryRepository()

final class InMemoryDictionaryNamespaceRepository extends DictionaryNamespaceRepository:
  private val state = InMemoryKeyValueStore[String, DictionaryNamespaceRegistration]()

  override def save(registration: DictionaryNamespaceRegistration): DictionaryNamespaceRegistration =
    val persisted = registration.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "dictionary-namespace",
        registration.namespacePrefix,
        registration.version,
        state.get(registration.namespacePrefix).map(_.version)
      )
    )
    state.upsert(persisted.namespacePrefix, persisted)
    persisted

  override def findByPrefix(prefix: String): Option[DictionaryNamespaceRegistration] =
    state.get(prefix)

  override def findAll(): Vector[DictionaryNamespaceRegistration] =
    state.values.sortBy(_.namespacePrefix)

object InMemoryDictionaryNamespaceRepository:
  def apply(): InMemoryDictionaryNamespaceRepository =
    new InMemoryDictionaryNamespaceRepository()

final class InMemoryTournamentSettlementRepository extends TournamentSettlementRepository:
  private val state = InMemoryKeyValueStore[SettlementSnapshotId, TournamentSettlementSnapshot]()

  override def save(snapshot: TournamentSettlementSnapshot): TournamentSettlementSnapshot =
    val persisted = snapshot.copy(
      version = InMemoryAggregateRepositoryLockSupport.nextVersion(
        "tournament-settlement",
        snapshot.id.value,
        snapshot.version,
        state.get(snapshot.id).map(_.version)
      )
    )
    state.upsert(persisted.id, persisted)
    persisted

  override def findById(id: SettlementSnapshotId): Option[TournamentSettlementSnapshot] =
    state.get(id)

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    state.values
      .filter(snapshot => snapshot.tournamentId == tournamentId && snapshot.stageId == stageId)
      .sortBy(snapshot => (snapshot.revision, snapshot.generatedAt))
      .lastOption

  override def findByTournament(tournamentId: TournamentId): Vector[TournamentSettlementSnapshot] =
    state.values.filter(_.tournamentId == tournamentId).sortBy(snapshot => (snapshot.generatedAt, snapshot.revision))

  override def findAll(): Vector[TournamentSettlementSnapshot] =
    state.values.sortBy(snapshot => (snapshot.generatedAt, snapshot.revision))

object InMemoryTournamentSettlementRepository:
  def apply(): InMemoryTournamentSettlementRepository =
    new InMemoryTournamentSettlementRepository()
