package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

final class PostgresTableRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TableRepository:
  override def save(table: Table): Table =
    connectionFactory.withConnection(TournamentGameTable.save(_, table))

  override def delete(id: TableId): Unit =
    connectionFactory.withConnection(TournamentGameTable.delete(_, id))

  override def findById(id: TableId): Option[Table] =
    connectionFactory.withConnection(TournamentGameTable.findById(_, id))

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    connectionFactory.withConnection(TournamentGameTable.findByTournamentAndStage(_, tournamentId, stageId))

  override def findByTournamentIds(tournamentIds: Vector[TournamentId]): Vector[Table] =
    connectionFactory.withConnection(TournamentGameTable.findByTournamentIds(_, tournamentIds))

  override def findAll(): Vector[Table] =
    connectionFactory.withConnection(TournamentGameTable.findAll)

object PostgresTableRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTableRepository =
    new PostgresTableRepository(connectionFactory)
