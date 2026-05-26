package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable

final class PostgresMatchRecordRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends MatchRecordRepository:
  override def save(record: MatchRecord): MatchRecord =
    connectionFactory.withConnection(MatchRecordTable.save(_, record))

  override def findById(id: MatchRecordId): Option[MatchRecord] =
    connectionFactory.withConnection(MatchRecordTable.findById(_, id))

  override def findByTable(tableId: TableId): Option[MatchRecord] =
    connectionFactory.withConnection(MatchRecordTable.findByTable(_, tableId))

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    connectionFactory.withConnection(MatchRecordTable.findByTournamentAndStage(_, tournamentId, stageId))

  override def findRecentByClub(clubId: ClubId, limit: Int): Vector[MatchRecord] =
    connectionFactory.withConnection(MatchRecordTable.findRecentByClub(_, clubId, limit))

  override def findAll(): Vector[MatchRecord] =
    connectionFactory.withConnection(MatchRecordTable.findAll)

object PostgresMatchRecordRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresMatchRecordRepository =
    new PostgresMatchRecordRepository(connectionFactory)
