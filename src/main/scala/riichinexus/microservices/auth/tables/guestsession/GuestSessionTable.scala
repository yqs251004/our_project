package riichinexus.microservices.auth.tables.guestsession

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.GuestSessionId
import riichinexus.microservices.auth.domain.model.GuestAccessSession
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object GuestSessionTable:
  private val upsertSql: String =
    """
      |insert into guest_sessions (id, created_at, display_name, payload, updated_at)
      |values (?, ?, ?, cast(? as jsonb), now())
      |on conflict (id) do update set
      |  created_at = excluded.created_at,
      |  display_name = excluded.display_name,
      |  payload = excluded.payload,
      |  updated_at = now()
      |where cast(guest_sessions.payload ->> 'version' as integer) = ?
      |""".stripMargin

  private[riichinexus] def save(connection: Connection, session: GuestAccessSession): GuestAccessSession =
    val persisted = session.copy(version = session.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setTimestamp(2, Timestamp.from(persisted.createdAt))
      statement.setString(3, persisted.displayName)
      statement.setString(4, write[GuestAccessSession](persisted))
      statement.setInt(5, session.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "guest-session",
        aggregateId = persisted.id.value,
        expectedVersion = session.version,
        actualVersion = findById(connection, persisted.id).map(_.version)
      )
    persisted

  private val findByIdSql: String =
    """
      |select payload
      |from guest_sessions
      |where id = ?
      |""".stripMargin

  private[riichinexus] def findById(connection: Connection, id: GuestSessionId): Option[GuestAccessSession] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readSession(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from guest_sessions
      |order by created_at desc
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[GuestAccessSession] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readSessions)
    }

  private def readSessions(resultSet: ResultSet): Vector[GuestAccessSession] =
    @tailrec
    def loop(acc: Vector[GuestAccessSession]): Vector[GuestAccessSession] =
      if resultSet.next() then loop(readSession(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readSession(resultSet: ResultSet): GuestAccessSession =
    read[GuestAccessSession](resultSet.getString("payload"))
