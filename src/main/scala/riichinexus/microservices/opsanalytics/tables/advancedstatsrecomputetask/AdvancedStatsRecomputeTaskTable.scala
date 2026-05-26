package riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask

import java.sql.{Connection, ResultSet, Timestamp}
import java.time.Instant

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{
  AdvancedStatsRecomputeTask,
  AdvancedStatsRecomputeTaskStatus,
  DashboardOwner
}
import upickle.default.{read, write}

object AdvancedStatsRecomputeTaskTable:
  private val upsertSql: String =
    """
      |insert into advanced_stats_recompute_tasks (
      |  id, owner_key, owner_type, status, calculator_version, requested_at, payload, updated_at
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

  private[riichinexus] def save(connection: Connection, task: AdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask =
    val persisted = task.copy(version = task.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setString(2, ownerKey(persisted.owner))
      statement.setString(3, ownerType(persisted.owner))
      statement.setString(4, persisted.status.toString)
      statement.setInt(5, persisted.calculatorVersion)
      statement.setTimestamp(6, Timestamp.from(persisted.requestedAt))
      statement.setString(7, write[AdvancedStatsRecomputeTask](persisted))
      statement.setInt(8, task.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "advanced-stats-task",
        aggregateId = persisted.id.value,
        expectedVersion = task.version,
        actualVersion = findById(connection, persisted.id).map(_.version)
      )
    persisted

  private val findByIdSql: String =
    """
      |select payload
      |from advanced_stats_recompute_tasks
      |where id = ?
      |""".stripMargin

  private[riichinexus] def findById(
      connection: Connection,
      id: AdvancedStatsRecomputeTaskId
  ): Option[AdvancedStatsRecomputeTask] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readTask(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from advanced_stats_recompute_tasks
      |order by requested_at, id
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[AdvancedStatsRecomputeTask] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readTasks)
    }

  private val findPendingSql: String =
    """
      |select payload
      |from advanced_stats_recompute_tasks
      |where status = ?
      |order by requested_at, id
      |""".stripMargin

  private[riichinexus] def findPending(
      connection: Connection,
      limit: Int,
      asOf: Instant = Instant.now()
  ): Vector[AdvancedStatsRecomputeTask] =
    Using.resource(connection.prepareStatement(findPendingSql)) { statement =>
      statement.setString(1, AdvancedStatsRecomputeTaskStatus.Pending.toString)
      Using.resource(statement.executeQuery())(readTasks)
    }
      .filter(_.isRunnable(asOf))
      .take(limit)

  private val findActiveByOwnerSql: String =
    """
      |select payload
      |from advanced_stats_recompute_tasks
      |where owner_key = ?
      |  and calculator_version = ?
      |  and status in (?, ?)
      |order by requested_at
      |limit 1
      |""".stripMargin

  private[riichinexus] def findActiveByOwner(
      connection: Connection,
      owner: DashboardOwner,
      calculatorVersion: Int
  ): Option[AdvancedStatsRecomputeTask] =
    Using.resource(connection.prepareStatement(findActiveByOwnerSql)) { statement =>
      statement.setString(1, ownerKey(owner))
      statement.setInt(2, calculatorVersion)
      statement.setString(3, AdvancedStatsRecomputeTaskStatus.Pending.toString)
      statement.setString(4, AdvancedStatsRecomputeTaskStatus.Processing.toString)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readTask(resultSet))
        else None
      }
    }

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

  private def ownerType(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(_) => "player"
      case DashboardOwner.Club(_)   => "club"

  private def readTasks(resultSet: ResultSet): Vector[AdvancedStatsRecomputeTask] =
    @tailrec
    def loop(acc: Vector[AdvancedStatsRecomputeTask]): Vector[AdvancedStatsRecomputeTask] =
      if resultSet.next() then loop(readTask(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readTask(resultSet: ResultSet): AdvancedStatsRecomputeTask =
    read[AdvancedStatsRecomputeTask](resultSet.getString("payload"))
