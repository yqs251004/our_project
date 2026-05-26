package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable

final class PostgresAdvancedStatsBoardRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AdvancedStatsBoardRepository:
  override def save(board: AdvancedStatsBoard): AdvancedStatsBoard =
    connectionFactory.withConnection(AdvancedStatsBoardTable.save(_, board))

  override def findByOwner(owner: DashboardOwner): Option[AdvancedStatsBoard] =
    connectionFactory.withConnection(AdvancedStatsBoardTable.findByOwner(_, owner))

  override def findAll(): Vector[AdvancedStatsBoard] =
    connectionFactory.withConnection(AdvancedStatsBoardTable.findAll)

object PostgresAdvancedStatsBoardRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAdvancedStatsBoardRepository =
    new PostgresAdvancedStatsBoardRepository(connectionFactory)
