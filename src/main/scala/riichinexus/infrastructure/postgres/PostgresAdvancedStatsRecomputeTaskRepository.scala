package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresAdvancedStatsRecomputeTaskRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AdvancedStatsRecomputeTaskRepository
    with JdbcRepositorySupport:
  override def save(task: AdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask =
    val persisted = task.copy(version = task.version + 1)
    val sql =
      """
        |insert into advanced_stats_recompute_tasks (
        |  id,
        |  owner_key,
        |  owner_type,
        |  status,
        |  calculator_version,
        |  requested_at,
        |  payload,
        |  updated_at
        |)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  owner_key = excluded.owner_key,
        |  owner_type = excluded.owner_type,
        |  status = excluded.status,
        |  calculator_version = excluded.calculator_version,
        |  requested_at = excluded.requested_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(advanced_stats_recompute_tasks.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, ownerKey(persisted.owner))
        statement.setString(3, ownerType(persisted.owner))
        statement.setString(4, persisted.status.toString)
        statement.setInt(5, persisted.calculatorVersion)
        statement.setTimestamp(6, Timestamp.from(persisted.requestedAt))
        statement.setString(7, writeJson[AdvancedStatsRecomputeTask](persisted))
        statement.setInt(8, task.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "advanced-stats-task",
      persisted.id.value,
      task.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: AdvancedStatsRecomputeTaskId): Option[AdvancedStatsRecomputeTask] =
    readOne[AdvancedStatsRecomputeTask](
      "select payload from advanced_stats_recompute_tasks where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[AdvancedStatsRecomputeTask] =
    readAll[AdvancedStatsRecomputeTask](
      "select payload from advanced_stats_recompute_tasks order by requested_at, id"
    )

  override def findPending(
      limit: Int,
      asOf: java.time.Instant = java.time.Instant.now()
  ): Vector[AdvancedStatsRecomputeTask] =
    readAll[AdvancedStatsRecomputeTask](
      "select payload from advanced_stats_recompute_tasks where status = ? order by requested_at, id",
      { statement =>
        statement.setString(1, AdvancedStatsRecomputeTaskStatus.Pending.toString)
      }
    )
      .filter(_.isRunnable(asOf))
      .take(limit)

  override def findActiveByOwner(
      owner: DashboardOwner,
      calculatorVersion: Int
  ): Option[AdvancedStatsRecomputeTask] =
    readOne[AdvancedStatsRecomputeTask](
      """
        |select payload
        |from advanced_stats_recompute_tasks
        |where owner_key = ?
        |  and calculator_version = ?
        |  and status in (?, ?)
        |order by requested_at
        |limit 1
        |""".stripMargin,
      { statement =>
        statement.setString(1, ownerKey(owner))
        statement.setInt(2, calculatorVersion)
        statement.setString(3, AdvancedStatsRecomputeTaskStatus.Pending.toString)
        statement.setString(4, AdvancedStatsRecomputeTaskStatus.Processing.toString)
      }
    )

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

  private def ownerType(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(_) => "player"
      case DashboardOwner.Club(_)   => "club"

object PostgresAdvancedStatsRecomputeTaskRepository:
  def apply(
      connectionFactory: JdbcConnectionFactory
  ): PostgresAdvancedStatsRecomputeTaskRepository =
    new PostgresAdvancedStatsRecomputeTaskRepository(connectionFactory)
