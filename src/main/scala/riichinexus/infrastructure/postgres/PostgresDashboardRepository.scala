package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable

final class PostgresDashboardRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DashboardRepository:
  override def save(dashboard: Dashboard): Dashboard =
    connectionFactory.withConnection(DashboardTable.save(_, dashboard))

  override def findByOwner(owner: DashboardOwner): Option[Dashboard] =
    connectionFactory.withConnection(DashboardTable.findByOwner(_, owner))

  override def findAll(): Vector[Dashboard] =
    connectionFactory.withConnection(DashboardTable.findAll)

object PostgresDashboardRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDashboardRepository =
    new PostgresDashboardRepository(connectionFactory)
