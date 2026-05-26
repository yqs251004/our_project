package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsRecomputeTask, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTable

final class PostgresAdvancedStatsRecomputeTaskRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AdvancedStatsRecomputeTaskRepository:
  override def save(task: AdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask =
    connectionFactory.withConnection(AdvancedStatsRecomputeTaskTable.save(_, task))

  override def findById(id: AdvancedStatsRecomputeTaskId): Option[AdvancedStatsRecomputeTask] =
    connectionFactory.withConnection(AdvancedStatsRecomputeTaskTable.findById(_, id))

  override def findAll(): Vector[AdvancedStatsRecomputeTask] =
    connectionFactory.withConnection(AdvancedStatsRecomputeTaskTable.findAll)

  override def findPending(
      limit: Int,
      asOf: java.time.Instant = java.time.Instant.now()
  ): Vector[AdvancedStatsRecomputeTask] =
    connectionFactory.withConnection(AdvancedStatsRecomputeTaskTable.findPending(_, limit, asOf))

  override def findActiveByOwner(
      owner: DashboardOwner,
      calculatorVersion: Int
  ): Option[AdvancedStatsRecomputeTask] =
    connectionFactory.withConnection(AdvancedStatsRecomputeTaskTable.findActiveByOwner(_, owner, calculatorVersion))

object PostgresAdvancedStatsRecomputeTaskRepository:
  def apply(
      connectionFactory: JdbcConnectionFactory
  ): PostgresAdvancedStatsRecomputeTaskRepository =
    new PostgresAdvancedStatsRecomputeTaskRepository(connectionFactory)
