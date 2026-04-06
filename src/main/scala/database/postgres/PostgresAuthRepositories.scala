package database.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import org.postgresql.util.PSQLException

import json.JsonCodecs.given
import ports.*
import riichinexus.domain.model.*

private object PostgresAuthErrors:
  private val UniqueViolation = "23505"

  def isUniqueViolation(error: SQLException, constraint: String): Boolean =
    Option(error.getSQLState).contains(UniqueViolation) &&
      constraintName(error).contains(constraint)

  private def constraintName(error: SQLException): Option[String] =
    error match
      case postgresError: PSQLException =>
        Option(postgresError.getServerErrorMessage).map(_.getConstraint)
      case _ =>
        None

final class PostgresAccountCredentialRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AccountCredentialRepository
    with JdbcRepositorySupport:
  override def save(credential: AccountCredential): AccountCredential =
    try persist(credential)
    catch
      case error: SQLException if PostgresAuthErrors.isUniqueViolation(error, "idx_account_credentials_player_id") =>
        throw IllegalArgumentException(s"Player ${credential.playerId.value} already has a registered account")

  private def persist(credential: AccountCredential): AccountCredential =
    val persisted = credential.copy(version = credential.version + 1)
    val sql =
      """
        |insert into account_credentials (username, player_id, payload, updated_at)
        |values (?, ?, cast(? as jsonb), now())
        |on conflict (username) do update set
        |  player_id = excluded.player_id,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(account_credentials.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.username)
        statement.setString(2, persisted.playerId.value)
        statement.setString(3, writeJson[AccountCredential](persisted))
        statement.setInt(4, credential.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "account-credential",
      persisted.username,
      credential.version,
      findByUsername(persisted.username).map(_.version)
    )
    persisted

  override def findByUsername(username: String): Option[AccountCredential] =
    readOne[AccountCredential]("select payload from account_credentials where username = ?", { statement =>
      statement.setString(1, AccountCredential.normalizeUsername(username))
    })

  override def findByPlayerId(playerId: PlayerId): Option[AccountCredential] =
    readOne[AccountCredential]("select payload from account_credentials where player_id = ?", { statement =>
      statement.setString(1, playerId.value)
    })

  override def findAll(): Vector[AccountCredential] =
    readAll[AccountCredential]("select payload from account_credentials order by username")

object PostgresAccountCredentialRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAccountCredentialRepository =
    new PostgresAccountCredentialRepository(connectionFactory)

final class PostgresAuthenticatedSessionRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AuthenticatedSessionRepository
    with JdbcRepositorySupport:
  override def save(session: AuthenticatedSession): AuthenticatedSession =
    val persisted = session.copy(version = session.version + 1)
    val sql =
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

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.token)
        statement.setString(2, persisted.username)
        statement.setString(3, persisted.playerId.value)
        statement.setTimestamp(4, Timestamp.from(persisted.createdAt))
        statement.setTimestamp(5, Timestamp.from(persisted.expiresAt))
        statement.setString(6, writeJson[AuthenticatedSession](persisted))
        statement.setInt(7, session.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "authenticated-session",
      persisted.token,
      session.version,
      findByToken(persisted.token).map(_.version)
    )
    persisted

  override def findByToken(token: String): Option[AuthenticatedSession] =
    readOne[AuthenticatedSession]("select payload from authenticated_sessions where token = ?", { statement =>
      statement.setString(1, token)
    })

  override def findAll(): Vector[AuthenticatedSession] =
    readAll[AuthenticatedSession](
      "select payload from authenticated_sessions order by created_at desc"
    )

object PostgresAuthenticatedSessionRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAuthenticatedSessionRepository =
    new PostgresAuthenticatedSessionRepository(connectionFactory)
