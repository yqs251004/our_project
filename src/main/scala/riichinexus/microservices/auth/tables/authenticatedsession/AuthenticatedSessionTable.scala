package riichinexus.microservices.auth.tables.authenticatedsession

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.domain.functions.AuthenticatedSessionFunctions
import riichinexus.microservices.auth.domain.model.AuthenticatedSession
import upickle.default.{read, write}

object AuthenticatedSessionTable:
  private val upsertSql: String =
    """
      |insert into authenticated_sessions (token, username, player_id, created_at, expires_at, payload, updated_at)
      |values (?, ?, ?, ?, ?, cast(? as jsonb), now())
      |on conflict (token) do update set
      |  username = excluded.username,
      |  player_id = excluded.player_id,
      |  created_at = excluded.created_at,
      |  expires_at = excluded.expires_at,
      |  payload = excluded.payload,
      |  updated_at = now()
      |where cast(authenticated_sessions.payload ->> 'version' as integer) = ?
      |""".stripMargin

  private[auth] def save(connection: Connection, session: AuthenticatedSession): AuthenticatedSession =
    AuthenticatedSessionFunctions.validate(session)
    val persisted = session.copy(version = session.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.token)
      statement.setString(2, persisted.username)
      statement.setString(3, persisted.playerId.value)
      statement.setTimestamp(4, Timestamp.from(persisted.createdAt))
      statement.setTimestamp(5, Timestamp.from(persisted.expiresAt))
      statement.setString(6, write[AuthenticatedSession](persisted))
      statement.setInt(7, session.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "authenticated-session",
        aggregateId = persisted.token,
        expectedVersion = session.version,
        actualVersion = findByToken(connection, persisted.token).map(_.version)
      )
    persisted

  private val findByTokenSql: String =
    """
      |select payload
      |from authenticated_sessions
      |where token = ?
      |""".stripMargin

  private[auth] def findByToken(connection: Connection, token: String): Option[AuthenticatedSession] =
    Using.resource(connection.prepareStatement(findByTokenSql)) { statement =>
      statement.setString(1, token)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readSession(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from authenticated_sessions
      |order by created_at desc
      |""".stripMargin

  private[auth] def findAll(connection: Connection): Vector[AuthenticatedSession] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readSessions)
    }

  private def readSessions(resultSet: ResultSet): Vector[AuthenticatedSession] =
    @tailrec
    def loop(acc: Vector[AuthenticatedSession]): Vector[AuthenticatedSession] =
      if resultSet.next() then loop(readSession(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readSession(resultSet: ResultSet): AuthenticatedSession =
    read[AuthenticatedSession](resultSet.getString("payload"))
