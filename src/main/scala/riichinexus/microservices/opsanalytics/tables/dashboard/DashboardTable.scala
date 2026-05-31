package riichinexus.microservices.opsanalytics.tables.dashboard

import java.sql.{Connection, ResultSet}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import upickle.default.{read, write}

object DashboardTable:
  private val upsertSql: String =
    """
      |insert into dashboards (owner_key, owner_type, payload, updated_at)
      |values (?, ?, cast(? as jsonb), now())
      |on conflict (owner_key) do update set
      |  owner_type = excluded.owner_type,
      |  payload = excluded.payload,
      |  updated_at = now()
      |where cast(dashboards.payload ->> 'version' as integer) = ?
      |""".stripMargin

  private[opsanalytics] def save(connection: Connection, dashboard: Dashboard): Dashboard =
    val persisted = dashboard.copy(version = dashboard.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, ownerKey(persisted.owner))
      statement.setString(2, ownerType(persisted.owner))
      statement.setString(3, write[Dashboard](persisted))
      statement.setInt(4, dashboard.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "dashboard",
        aggregateId = ownerKey(persisted.owner),
        expectedVersion = dashboard.version,
        actualVersion = findByOwner(connection, persisted.owner).map(_.version)
      )
    persisted

  private val findByOwnerSql: String =
    """
      |select payload
      |from dashboards
      |where owner_key = ?
      |""".stripMargin

  private[opsanalytics] def findByOwner(connection: Connection, owner: DashboardOwner): Option[Dashboard] =
    Using.resource(connection.prepareStatement(findByOwnerSql)) { statement =>
      statement.setString(1, ownerKey(owner))
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readDashboard(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from dashboards
      |order by owner_key
      |""".stripMargin

  private[opsanalytics] def findAll(connection: Connection): Vector[Dashboard] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readDashboards)
    }

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

  private def ownerType(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(_) => "player"
      case DashboardOwner.Club(_)   => "club"

  private def readDashboards(resultSet: ResultSet): Vector[Dashboard] =
    @tailrec
    def loop(acc: Vector[Dashboard]): Vector[Dashboard] =
      if resultSet.next() then loop(readDashboard(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readDashboard(resultSet: ResultSet): Dashboard =
    read[Dashboard](resultSet.getString("payload"))
