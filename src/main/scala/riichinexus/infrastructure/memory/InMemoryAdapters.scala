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

  override def findAll(): Vector[Player] =
    state.values.toVector

final class InMemoryClubRepository extends ClubRepository:
  private val state = mutable.LinkedHashMap.empty[ClubId, Club]

  override def save(club: Club): Club =
    state.update(club.id, club)
    club

  override def findById(id: ClubId): Option[Club] =
    state.get(id)

  override def findAll(): Vector[Club] =
    state.values.toVector

final class InMemoryTournamentRepository extends TournamentRepository:
  private val state = mutable.LinkedHashMap.empty[TournamentId, Tournament]

  override def save(tournament: Tournament): Tournament =
    state.update(tournament.id, tournament)
    tournament

  override def findById(id: TournamentId): Option[Tournament] =
    state.get(id)

  override def findAll(): Vector[Tournament] =
    state.values.toVector

final class InMemoryTableRepository extends TableRepository:
  private val state = mutable.LinkedHashMap.empty[TableId, Table]

  override def save(table: Table): Table =
    state.update(table.id, table)
    table

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

final class InMemoryPaifuRepository extends PaifuRepository:
  private val state = mutable.LinkedHashMap.empty[PaifuId, Paifu]

  override def save(paifu: Paifu): Paifu =
    state.update(paifu.id, paifu)
    paifu

  override def findById(id: PaifuId): Option[Paifu] =
    state.get(id)

  override def findAll(): Vector[Paifu] =
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

final class InMemoryDomainEventBus(
    initialSubscribers: Vector[DomainEventSubscriber] = Vector.empty
) extends DomainEventBus:
  private val subscribers = mutable.ArrayBuffer.from(initialSubscribers)

  override def publish(event: DomainEvent): Unit =
    subscribers.foreach(_.handle(event))

  override def register(subscriber: DomainEventSubscriber): Unit =
    subscribers += subscriber
