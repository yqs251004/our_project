package riichinexus.microservices.tournament.tables

import riichinexus.application.ports.{
  ClubRepository,
  MatchRecordRepository,
  PaifuRepository,
  PlayerRepository,
  TableRepository,
  TournamentRepository,
  TournamentSettlementRepository
}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.{StageTableQuery, TournamentSettlementQuery}

final class TournamentTables(
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    matchRecordRepository: MatchRecordRepository,
    paifuRepository: PaifuRepository,
    tournamentSettlementRepository: TournamentSettlementRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository
):
  def listTournaments(
      status: Option[TournamentStatus],
      adminId: Option[PlayerId],
      organizer: Option[String]
  ): Vector[Tournament] =
    tournamentRepository.findFiltered(
      status = status,
      adminId = adminId,
      organizer = organizer
    )

  def findTournament(tournamentId: TournamentId): Option[Tournament] =
    tournamentRepository.findById(tournamentId)

  def findPlayers(playerIds: Iterable[PlayerId]): Vector[Player] =
    playerRepository.findByIds(playerIds.toVector.distinct)

  def findClubs(clubIds: Iterable[ClubId]): Vector[Club] =
    clubRepository.findByIds(clubIds.toVector.distinct)

  def findClub(clubId: ClubId): Option[Club] =
    clubRepository.findById(clubId)

  def listTables(): Vector[Table] =
    tableRepository.findAll()

  def findTable(tableId: TableId): Option[Table] =
    tableRepository.findById(tableId)

  def listTournamentTables(tournamentId: TournamentId): Vector[Table] =
    tableRepository.findByTournamentIds(Vector(tournamentId))

  def listStageTables(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      query: StageTableQuery
  ): Vector[Table] =
    tableRepository.findByTournamentAndStage(tournamentId, stageId)
      .filter(table => query.status.forall(_ == table.status))
      .filter(table => query.roundNumber.forall(_ == table.stageRoundNumber))
      .filter(table => query.playerId.forall(playerId => table.seats.exists(_.playerId == playerId)))
      .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))

  def listMatchRecords(): Vector[MatchRecord] =
    matchRecordRepository.findAll()

  def listStageMatchRecords(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    matchRecordRepository.findByTournamentAndStage(tournamentId, stageId)

  def findMatchRecord(recordId: MatchRecordId): Option[MatchRecord] =
    matchRecordRepository.findById(recordId)

  def findMatchRecordByTable(tableId: TableId): Option[MatchRecord] =
    matchRecordRepository.findByTable(tableId)

  def listPaifus(): Vector[Paifu] =
    paifuRepository.findAll()

  def findPaifu(paifuId: PaifuId): Option[Paifu] =
    paifuRepository.findById(paifuId)

  def listSettlements(
      tournamentId: TournamentId,
      query: TournamentSettlementQuery
  ): Vector[TournamentSettlementSnapshot] =
    tournamentSettlementRepository.findByTournament(tournamentId)
      .filter(snapshot => query.stageId.forall(_ == snapshot.stageId))
      .filter(snapshot => query.status.forall(_ == snapshot.status))
      .filter(snapshot => query.championId.forall(_ == snapshot.championId))
      .sortBy(snapshot => (snapshot.generatedAt, snapshot.revision))

  def findSettlement(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    tournamentSettlementRepository.findByTournamentAndStage(tournamentId, stageId)
