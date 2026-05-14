package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresDashboardRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DashboardRepository
    with JdbcRepositorySupport:
  override def save(dashboard: Dashboard): Dashboard =
    val persisted = dashboard.copy(version = dashboard.version + 1)
    val sql =
      """
        |insert into dashboards (owner_key, owner_type, payload, updated_at)
        |values (?, ?, cast(? as jsonb), now())
        |on conflict (owner_key) do update set
        |  owner_type = excluded.owner_type,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(dashboards.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, ownerKey(persisted.owner))
        statement.setString(2, ownerType(persisted.owner))
        statement.setString(3, writeJson[Dashboard](persisted))
        statement.setInt(4, dashboard.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "dashboard",
      ownerKey(persisted.owner),
      dashboard.version,
      findByOwner(persisted.owner).map(_.version)
    )
    persisted

  override def findByOwner(owner: DashboardOwner): Option[Dashboard] =
    readOne[Dashboard]("select payload from dashboards where owner_key = ?", { statement =>
      statement.setString(1, ownerKey(owner))
    })

  override def findAll(): Vector[Dashboard] =
    readAll[Dashboard]("select payload from dashboards order by owner_key")

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

  private def ownerType(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(_) => "player"
      case DashboardOwner.Club(_)   => "club"

object PostgresDashboardRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDashboardRepository =
    new PostgresDashboardRepository(connectionFactory)
