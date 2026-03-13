package riichinexus.application.ports

import riichinexus.domain.model.*

trait PlayerRepository:
  def save(player: Player): Player
  def findById(id: PlayerId): Option[Player]
  def findByUserId(userId: String): Option[Player]
  def findAll(): Vector[Player]

trait ClubRepository:
  def save(club: Club): Club
  def findById(id: ClubId): Option[Club]
  def findByName(name: String): Option[Club]
  def findAll(): Vector[Club]

trait TournamentRepository:
  def save(tournament: Tournament): Tournament
  def findById(id: TournamentId): Option[Tournament]
  def findByNameAndOrganizer(name: String, organizer: String): Option[Tournament]
  def findAll(): Vector[Tournament]

trait TableRepository:
  def save(table: Table): Table
  def findById(id: TableId): Option[Table]
  def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table]
  def findAll(): Vector[Table]

trait PaifuRepository:
  def save(paifu: Paifu): Paifu
  def findById(id: PaifuId): Option[Paifu]
  def findAll(): Vector[Paifu]

  def findByPlayer(playerId: PlayerId): Vector[Paifu] =
    findAll().filter(_.playerIds.contains(playerId))

trait DashboardRepository:
  def save(dashboard: Dashboard): Dashboard
  def findByOwner(owner: DashboardOwner): Option[Dashboard]
  def findAll(): Vector[Dashboard]
