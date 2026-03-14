package riichinexus.application.ports

import riichinexus.domain.model.*

trait PlayerRepository:
  def save(player: Player): Player
  def findById(id: PlayerId): Option[Player]
  def findByUserId(userId: String): Option[Player]
  def findAll(): Vector[Player]

  def findByClub(clubId: ClubId): Vector[Player] =
    findAll().filter(_.boundClubIds.contains(clubId))

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

trait GlobalDictionaryRepository:
  def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry
  def findByKey(key: String): Option[GlobalDictionaryEntry]
  def findAll(): Vector[GlobalDictionaryEntry]

trait TournamentSettlementRepository:
  def save(snapshot: TournamentSettlementSnapshot): TournamentSettlementSnapshot
  def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot]
  def findByTournament(tournamentId: TournamentId): Vector[TournamentSettlementSnapshot]
  def findAll(): Vector[TournamentSettlementSnapshot]

trait AuditEventRepository:
  def save(entry: AuditEventEntry): AuditEventEntry
  def findByAggregate(aggregateType: String, aggregateId: String): Vector[AuditEventEntry]
  def findAll(): Vector[AuditEventEntry]
