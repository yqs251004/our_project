package riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask

import java.sql.{Connection, ResultSet, Timestamp}
import java.time.Instant

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.system.errors.OptimisticConcurrencyException
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.{AdvancedStatsRecomputeTaskFunctions, DashboardFunctions}
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

  private[opsanalytics] def save(connection: Connection, task: AdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask =
    AdvancedStatsRecomputeTaskFunctions.validate(task)
    val persisted = task.copy(version = task.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setString(2, DashboardFunctions.ownerKey(persisted.owner))
      statement.setString(3, DashboardFunctions.ownerType(persisted.owner))
      statement.setString(4, AdvancedStatsRecomputeTaskStatus.toString(persisted.status))
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

  private[opsanalytics] def findById(
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

  private[opsanalytics] def findAll(connection: Connection): Vector[AdvancedStatsRecomputeTask] =
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

  private[opsanalytics] def findPending(
      connection: Connection,
      limit: Int,
      asOf: Instant = Instant.now()
  ): Vector[AdvancedStatsRecomputeTask] =
    Using.resource(connection.prepareStatement(findPendingSql)) { statement =>
      statement.setString(1, AdvancedStatsRecomputeTaskStatus.toString(AdvancedStatsRecomputeTaskStatus.Pending))
      Using.resource(statement.executeQuery())(readTasks)
    }
      .filter(AdvancedStatsRecomputeTaskFunctions.isRunnable(_, asOf))
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

  private[opsanalytics] def findActiveByOwner(
      connection: Connection,
      owner: DashboardOwner,
      calculatorVersion: Int
  ): Option[AdvancedStatsRecomputeTask] =
    Using.resource(connection.prepareStatement(findActiveByOwnerSql)) { statement =>
      statement.setString(1, DashboardFunctions.ownerKey(owner))
      statement.setInt(2, calculatorVersion)
      statement.setString(3, AdvancedStatsRecomputeTaskStatus.toString(AdvancedStatsRecomputeTaskStatus.Pending))
      statement.setString(4, AdvancedStatsRecomputeTaskStatus.toString(AdvancedStatsRecomputeTaskStatus.Processing))
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readTask(resultSet))
        else None
      }
    }

  private def readTasks(resultSet: ResultSet): Vector[AdvancedStatsRecomputeTask] =
    @tailrec
    def loop(acc: Vector[AdvancedStatsRecomputeTask]): Vector[AdvancedStatsRecomputeTask] =
      if resultSet.next() then loop(readTask(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readTask(resultSet: ResultSet): AdvancedStatsRecomputeTask =
    read[AdvancedStatsRecomputeTask](resultSet.getString("payload"))
