package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTable

final class PostgresTournamentSettlementRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TournamentSettlementRepository:
  override def save(snapshot: TournamentSettlementSnapshot): TournamentSettlementSnapshot =
    connectionFactory.withConnection(TournamentSettlementTable.save(_, snapshot))

  override def findById(id: SettlementSnapshotId): Option[TournamentSettlementSnapshot] =
    connectionFactory.withConnection(TournamentSettlementTable.findById(_, id))

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    connectionFactory.withConnection(TournamentSettlementTable.findByTournamentAndStage(_, tournamentId, stageId))

  override def findByTournament(tournamentId: TournamentId): Vector[TournamentSettlementSnapshot] =
    connectionFactory.withConnection(TournamentSettlementTable.findByTournament(_, tournamentId))

  override def findAll(): Vector[TournamentSettlementSnapshot] =
    connectionFactory.withConnection(TournamentSettlementTable.findAll)

object PostgresTournamentSettlementRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTournamentSettlementRepository =
    new PostgresTournamentSettlementRepository(connectionFactory)
