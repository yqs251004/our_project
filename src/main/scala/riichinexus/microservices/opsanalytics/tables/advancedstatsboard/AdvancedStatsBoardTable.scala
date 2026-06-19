package riichinexus.microservices.opsanalytics.tables.advancedstatsboard

import java.sql.{Connection, ResultSet}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.system.errors.OptimisticConcurrencyException
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.DashboardFunctions
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import upickle.default.{read, write}

object AdvancedStatsBoardTable:
  private val upsertSql: String =
    """
      |insert into advanced_stats_boards (owner_key, owner_type, payload, updated_at)
      |values (?, ?, cast(? as jsonb), now())
      |on conflict (owner_key) do update set
      |  owner_type = excluded.owner_type,
      |  payload = excluded.payload,
      |  updated_at = now()
      |where cast(advanced_stats_boards.payload ->> 'version' as integer) = ?
      |""".stripMargin

  private[opsanalytics] def save(connection: Connection, board: AdvancedStatsBoard): AdvancedStatsBoard =
    val persisted = board.copy(version = board.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, DashboardFunctions.ownerKey(persisted.owner))
      statement.setString(2, DashboardFunctions.ownerType(persisted.owner))
      statement.setString(3, write[AdvancedStatsBoard](persisted))
      statement.setInt(4, board.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "advanced-stats-board",
        aggregateId = DashboardFunctions.ownerKey(persisted.owner),
        expectedVersion = board.version,
        actualVersion = findByOwner(connection, persisted.owner).map(_.version)
      )
    persisted

  private val findByOwnerSql: String =
    """
      |select payload
      |from advanced_stats_boards
      |where owner_key = ?
      |""".stripMargin

  private[opsanalytics] def findByOwner(connection: Connection, owner: DashboardOwner): Option[AdvancedStatsBoard] =
    Using.resource(connection.prepareStatement(findByOwnerSql)) { statement =>
      statement.setString(1, DashboardFunctions.ownerKey(owner))
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readBoard(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from advanced_stats_boards
      |order by owner_key
      |""".stripMargin

  private[opsanalytics] def findAll(connection: Connection): Vector[AdvancedStatsBoard] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readBoards)
    }

  private def readBoards(resultSet: ResultSet): Vector[AdvancedStatsBoard] =
    @tailrec
    def loop(acc: Vector[AdvancedStatsBoard]): Vector[AdvancedStatsBoard] =
      if resultSet.next() then loop(readBoard(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readBoard(resultSet: ResultSet): AdvancedStatsBoard =
    read[AdvancedStatsBoard](resultSet.getString("payload"))
