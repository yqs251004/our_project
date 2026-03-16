package riichinexus.infrastructure.memory

import scala.collection.mutable

import riichinexus.application.ports.*
import riichinexus.domain.event.DomainEvent
import riichinexus.domain.model.*

final class InMemoryPlayerRepository extends PlayerRepository:
  private val state = mutable.LinkedHashMap.empty[PlayerId, Player]

  override def save(player: Player): Player =
    state.update(player.id, player)
    player

  override def findById(id: PlayerId): Option[Player] =
    state.get(id)

  override def findByUserId(userId: String): Option[Player] =
    state.values.find(_.userId == userId)

  override def findAll(): Vector[Player] =
    state.values.toVector

final class InMemoryGuestSessionRepository extends GuestSessionRepository:
  private val state = mutable.LinkedHashMap.empty[GuestSessionId, GuestAccessSession]

  override def save(session: GuestAccessSession): GuestAccessSession =
    state.update(session.id, session)
    session

  override def findById(id: GuestSessionId): Option[GuestAccessSession] =
    state.get(id)

  override def findAll(): Vector[GuestAccessSession] =
    state.values.toVector

final class InMemoryClubRepository extends ClubRepository:
  private val state = mutable.LinkedHashMap.empty[ClubId, Club]

  override def save(club: Club): Club =
    state.update(club.id, club)
    club

  override def findById(id: ClubId): Option[Club] =
    state.get(id)

  override def findByName(name: String): Option[Club] =
    state.values.find(_.name == name)

  override def findAll(): Vector[Club] =
    state.values.toVector

final class InMemoryTournamentRepository extends TournamentRepository:
  private val state = mutable.LinkedHashMap.empty[TournamentId, Tournament]

  override def save(tournament: Tournament): Tournament =
    state.update(tournament.id, tournament)
    tournament

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
    state.update(table.id, table)
    table

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

final class InMemoryMatchRecordRepository extends MatchRecordRepository:
  private val state = mutable.LinkedHashMap.empty[MatchRecordId, MatchRecord]

  override def save(record: MatchRecord): MatchRecord =
    state.update(record.id, record)
    record

  override def findById(id: MatchRecordId): Option[MatchRecord] =
    state.get(id)

  override def findByTable(tableId: TableId): Option[MatchRecord] =
    state.values.find(_.tableId == tableId)

  override def findAll(): Vector[MatchRecord] =
    state.values.toVector

final class InMemoryPaifuRepository extends PaifuRepository:
  private val state = mutable.LinkedHashMap.empty[PaifuId, Paifu]

  override def save(paifu: Paifu): Paifu =
    state.update(paifu.id, paifu)
    paifu

  override def findById(id: PaifuId): Option[Paifu] =
    state.get(id)

  override def findAll(): Vector[Paifu] =
    state.values.toVector

final class InMemoryAppealTicketRepository extends AppealTicketRepository:
  private val state = mutable.LinkedHashMap.empty[AppealTicketId, AppealTicket]

  override def save(ticket: AppealTicket): AppealTicket =
    state.update(ticket.id, ticket)
    ticket

  override def findById(id: AppealTicketId): Option[AppealTicket] =
    state.get(id)

  override def findAll(): Vector[AppealTicket] =
    state.values.toVector

final class InMemoryDashboardRepository extends DashboardRepository:
  private val state = mutable.LinkedHashMap.empty[String, Dashboard]

  override def save(dashboard: Dashboard): Dashboard =
    state.update(ownerKey(dashboard.owner), dashboard)
    dashboard

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
    state.update(ownerKey(board.owner), board)
    board

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
    state.update(task.id, task)
    task

  override def findById(id: AdvancedStatsRecomputeTaskId): Option[AdvancedStatsRecomputeTask] =
    state.get(id)

  override def findAll(): Vector[AdvancedStatsRecomputeTask] =
    state.values.toVector.sortBy(_.requestedAt)

  override def findPending(limit: Int): Vector[AdvancedStatsRecomputeTask] =
    state.values
      .filter(task => task.status == AdvancedStatsRecomputeTaskStatus.Pending)
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
    state.update(entry.key, entry)
    entry

  override def findByKey(key: String): Option[GlobalDictionaryEntry] =
    state.get(key)

  override def findAll(): Vector[GlobalDictionaryEntry] =
    state.values.toVector

final class InMemoryDictionaryNamespaceRepository extends DictionaryNamespaceRepository:
  private val state = mutable.LinkedHashMap.empty[String, DictionaryNamespaceRegistration]

  override def save(registration: DictionaryNamespaceRegistration): DictionaryNamespaceRegistration =
    state.update(registration.namespacePrefix, registration)
    registration

  override def findByPrefix(prefix: String): Option[DictionaryNamespaceRegistration] =
    state.get(prefix)

  override def findAll(): Vector[DictionaryNamespaceRegistration] =
    state.values.toVector.sortBy(_.namespacePrefix)

final class InMemoryTournamentSettlementRepository extends TournamentSettlementRepository:
  private val state = mutable.LinkedHashMap.empty[(TournamentId, TournamentStageId), TournamentSettlementSnapshot]

  override def save(snapshot: TournamentSettlementSnapshot): TournamentSettlementSnapshot =
    state.update((snapshot.tournamentId, snapshot.stageId), snapshot)
    snapshot

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    state.get((tournamentId, stageId))

  override def findByTournament(tournamentId: TournamentId): Vector[TournamentSettlementSnapshot] =
    state.values.filter(_.tournamentId == tournamentId).toVector

  override def findAll(): Vector[TournamentSettlementSnapshot] =
    state.values.toVector

final class InMemoryEventCascadeRecordRepository extends EventCascadeRecordRepository:
  private val state = mutable.LinkedHashMap.empty[EventCascadeRecordId, EventCascadeRecord]

  override def save(record: EventCascadeRecord): EventCascadeRecord =
    state.update(record.id, record)
    record

  override def findById(id: EventCascadeRecordId): Option[EventCascadeRecord] =
    state.get(id)

  override def findAll(): Vector[EventCascadeRecord] =
    state.values.toVector.sortBy(record => (record.occurredAt, record.id.value))

final class InMemoryAuditEventRepository extends AuditEventRepository:
  private val state = mutable.ArrayBuffer.empty[AuditEventEntry]

  override def save(entry: AuditEventEntry): AuditEventEntry =
    state += entry
    entry

  override def findByAggregate(aggregateType: String, aggregateId: String): Vector[AuditEventEntry] =
    state.filter(entry => entry.aggregateType == aggregateType && entry.aggregateId == aggregateId).toVector

  override def findAll(): Vector[AuditEventEntry] =
    state.toVector

final class InMemoryDomainEventBus(
    initialSubscribers: Vector[DomainEventSubscriber] = Vector.empty
) extends DomainEventBus:
  private val subscribers = mutable.ArrayBuffer.from(initialSubscribers)

  override def publish(event: DomainEvent): Unit =
    subscribers.foreach(_.handle(event))

  override def register(subscriber: DomainEventSubscriber): Unit =
    subscribers += subscriber
