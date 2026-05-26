package riichinexus.infrastructure.audit.tables.auditevent

import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp, Types}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object AuditEventTable:
  private val upsertSql: String =
    """
      |insert into audit_events (id, aggregate_type, aggregate_id, event_type, occurred_at, actor_id, payload)
      |values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
      |on conflict (id) do update set
      |  aggregate_type = excluded.aggregate_type,
      |  aggregate_id = excluded.aggregate_id,
      |  event_type = excluded.event_type,
      |  occurred_at = excluded.occurred_at,
      |  actor_id = excluded.actor_id,
      |  payload = excluded.payload
      |""".stripMargin

  private[riichinexus] def save(connection: Connection, entry: AuditEventEntry): AuditEventEntry =
    Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, entry.id.value)
      statement.setString(2, entry.aggregateType)
      statement.setString(3, entry.aggregateId)
      statement.setString(4, entry.eventType)
      statement.setTimestamp(5, Timestamp.from(entry.occurredAt))
      setNullableString(statement, 6, entry.actorId.map(_.value))
      statement.setString(7, write[AuditEventEntry](entry))
      statement.executeUpdate()
    }
    entry

  private val findByAggregateSql: String =
    """
      |select payload
      |from audit_events
      |where aggregate_type = ? and aggregate_id = ?
      |order by occurred_at desc
      |""".stripMargin

  private[riichinexus] def findByAggregate(
      connection: Connection,
      aggregateType: String,
      aggregateId: String
  ): Vector[AuditEventEntry] =
    Using.resource(connection.prepareStatement(findByAggregateSql)) { statement =>
      statement.setString(1, aggregateType)
      statement.setString(2, aggregateId)
      Using.resource(statement.executeQuery())(readEntries)
    }

  private val findAllSql: String =
    """
      |select payload
      |from audit_events
      |order by occurred_at desc
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[AuditEventEntry] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readEntries)
    }

  private def setNullableString(
      statement: PreparedStatement,
      index: Int,
      value: Option[String]
  ): Unit =
    value match
      case Some(actual) => statement.setString(index, actual)
      case None         => statement.setNull(index, Types.VARCHAR)

  private def readEntries(resultSet: ResultSet): Vector[AuditEventEntry] =
    @tailrec
    def loop(acc: Vector[AuditEventEntry]): Vector[AuditEventEntry] =
      if resultSet.next() then loop(readEntry(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readEntry(resultSet: ResultSet): AuditEventEntry =
    read[AuditEventEntry](resultSet.getString("payload"))
