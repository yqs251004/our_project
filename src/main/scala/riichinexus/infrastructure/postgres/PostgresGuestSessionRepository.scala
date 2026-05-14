package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresGuestSessionRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends GuestSessionRepository
    with JdbcRepositorySupport:
  override def save(session: GuestAccessSession): GuestAccessSession =
    val persisted = session.copy(version = session.version + 1)
    val sql =
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

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setTimestamp(2, Timestamp.from(persisted.createdAt))
        statement.setString(3, persisted.displayName)
        statement.setString(4, writeJson[GuestAccessSession](persisted))
        statement.setInt(5, session.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "guest-session",
      persisted.id.value,
      session.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: GuestSessionId): Option[GuestAccessSession] =
    readOne[GuestAccessSession]("select payload from guest_sessions where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findAll(): Vector[GuestAccessSession] =
    readAll[GuestAccessSession]("select payload from guest_sessions order by created_at desc")

object PostgresGuestSessionRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresGuestSessionRepository =
    new PostgresGuestSessionRepository(connectionFactory)
