package riichinexus.microservices.audit.tables.auditevent
import riichinexus.microservices.audit.domain.model.AuditEvent
import riichinexus.microservices.audit.objects.`private`.AuditEventType
import riichinexus.system.objects.`private`.AggregateType

import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp, Types}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.system.json.JsonCodecs.given
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

  private[audit] def save(connection: Connection, event: AuditEvent): AuditEvent =
    Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, event.id.value)
      statement.setString(2, AggregateType.toString(event.aggregateType))
      statement.setString(3, event.aggregateId)
      statement.setString(4, event.eventType.toString)
      statement.setTimestamp(5, Timestamp.from(event.occurredAt))
      setNullableString(statement, 6, event.actorId.map(_.value))
      statement.setString(7, write[AuditEvent](event))
      statement.executeUpdate()
    }
    event

  private val findByAggregateSql: String =
    """
      |select payload
      |from audit_events
      |where aggregate_type = ? and aggregate_id = ?
      |order by occurred_at desc, id desc
      |""".stripMargin

  private[audit] def findByAggregate(
      connection: Connection,
      aggregateType: AggregateType,
      aggregateId: String
  ): Vector[AuditEvent] =
    Using.resource(connection.prepareStatement(findByAggregateSql)) { statement =>
      statement.setString(1, AggregateType.toString(aggregateType))
      statement.setString(2, aggregateId)
      Using.resource(statement.executeQuery())(readEvents)
    }

  private val findByAggregateOldestFirstSql: String =
    """
      |select payload
      |from audit_events
      |where aggregate_type = ? and aggregate_id = ?
      |order by occurred_at asc, id asc
      |""".stripMargin

  private[audit] def findByAggregateOldestFirst(
      connection: Connection,
      aggregateType: AggregateType,
      aggregateId: String
  ): Vector[AuditEvent] =
    Using.resource(connection.prepareStatement(findByAggregateOldestFirstSql)) { statement =>
      statement.setString(1, AggregateType.toString(aggregateType))
      statement.setString(2, aggregateId)
      Using.resource(statement.executeQuery())(readEvents)
    }

  private val findByAggregateAndEventTypeSql: String =
    """
      |select payload
      |from audit_events
      |where aggregate_type = ? and aggregate_id = ? and event_type = ?
      |order by occurred_at desc, id desc
      |""".stripMargin

  private[audit] def findByAggregateAndEventType(
      connection: Connection,
      aggregateType: AggregateType,
      aggregateId: String,
      eventType: AuditEventType
  ): Vector[AuditEvent] =
    Using.resource(connection.prepareStatement(findByAggregateAndEventTypeSql)) { statement =>
      statement.setString(1, AggregateType.toString(aggregateType))
      statement.setString(2, aggregateId)
      statement.setString(3, eventType.toString)
      Using.resource(statement.executeQuery())(readEvents)
    }

  private val findByAggregateAndEventTypeOldestFirstSql: String =
    """
      |select payload
      |from audit_events
      |where aggregate_type = ? and aggregate_id = ? and event_type = ?
      |order by occurred_at asc, id asc
      |""".stripMargin

  private[audit] def findByAggregateAndEventTypeOldestFirst(
      connection: Connection,
      aggregateType: AggregateType,
      aggregateId: String,
      eventType: AuditEventType
  ): Vector[AuditEvent] =
    Using.resource(connection.prepareStatement(findByAggregateAndEventTypeOldestFirstSql)) { statement =>
      statement.setString(1, AggregateType.toString(aggregateType))
      statement.setString(2, aggregateId)
      statement.setString(3, eventType.toString)
      Using.resource(statement.executeQuery())(readEvents)
    }

  private val findAllSql: String =
    """
      |select payload
      |from audit_events
      |order by occurred_at desc, id desc
      |""".stripMargin

  private[audit] def findAll(connection: Connection): Vector[AuditEvent] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readEvents)
    }

  private val findAllOldestFirstSql: String =
    """
      |select payload
      |from audit_events
      |order by occurred_at asc, id asc
      |""".stripMargin

  private[audit] def findAllOldestFirst(connection: Connection): Vector[AuditEvent] =
    Using.resource(connection.prepareStatement(findAllOldestFirstSql)) { statement =>
      Using.resource(statement.executeQuery())(readEvents)
    }

  private def setNullableString(
      statement: PreparedStatement,
      index: Int,
      value: Option[String]
  ): Unit =
    value match
      case Some(actual) => statement.setString(index, actual)
      case None         => statement.setNull(index, Types.VARCHAR)

  private def readEvents(resultSet: ResultSet): Vector[AuditEvent] =
    @tailrec
    def loop(acc: Vector[AuditEvent]): Vector[AuditEvent] =
      if resultSet.next() then loop(readEvent(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readEvent(resultSet: ResultSet): AuditEvent =
    read[AuditEvent](resultSet.getString("payload"))
