package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresDictionaryNamespaceRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DictionaryNamespaceRepository
    with JdbcRepositorySupport:
  override def save(registration: DictionaryNamespaceRegistration): DictionaryNamespaceRegistration =
    val persisted = registration.copy(version = registration.version + 1)
    val sql =
      """
        |insert into dictionary_namespaces (namespace_prefix, owner_player_id, status, requested_at, payload, updated_at)
        |values (?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (namespace_prefix) do update set
        |  owner_player_id = excluded.owner_player_id,
        |  status = excluded.status,
        |  requested_at = excluded.requested_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(dictionary_namespaces.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.namespacePrefix)
        statement.setString(2, persisted.ownerPlayerId.value)
        statement.setString(3, persisted.status.toString)
        statement.setTimestamp(4, Timestamp.from(persisted.requestedAt))
        statement.setString(5, writeJson[DictionaryNamespaceRegistration](persisted))
        statement.setInt(6, registration.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "dictionary-namespace",
      persisted.namespacePrefix,
      registration.version,
      findByPrefix(persisted.namespacePrefix).map(_.version)
    )
    persisted

  override def findByPrefix(prefix: String): Option[DictionaryNamespaceRegistration] =
    readOne[DictionaryNamespaceRegistration](
      "select payload from dictionary_namespaces where namespace_prefix = ?",
      { statement => statement.setString(1, prefix) }
    )

  override def findAll(): Vector[DictionaryNamespaceRegistration] =
    readAll[DictionaryNamespaceRegistration](
      "select payload from dictionary_namespaces order by namespace_prefix"
    )

object PostgresDictionaryNamespaceRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDictionaryNamespaceRepository =
    new PostgresDictionaryNamespaceRepository(connectionFactory)
