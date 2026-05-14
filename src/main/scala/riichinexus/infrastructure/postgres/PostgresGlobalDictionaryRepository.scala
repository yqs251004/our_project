package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresGlobalDictionaryRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends GlobalDictionaryRepository
    with JdbcRepositorySupport:
  override def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry =
    val persisted = entry.copy(version = entry.version + 1)
    val sql =
      """
        |insert into global_dictionary (key, updated_at, payload)
        |values (?, ?, cast(? as jsonb))
        |on conflict (key) do update set
        |  updated_at = excluded.updated_at,
        |  payload = excluded.payload
        |where cast(global_dictionary.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.key)
        statement.setTimestamp(2, Timestamp.from(persisted.updatedAt))
        statement.setString(3, writeJson[GlobalDictionaryEntry](persisted))
        statement.setInt(4, entry.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "global-dictionary-entry",
      persisted.key,
      entry.version,
      findByKey(persisted.key).map(_.version)
    )
    persisted

  override def findByKey(key: String): Option[GlobalDictionaryEntry] =
    readOne[GlobalDictionaryEntry](
      "select payload from global_dictionary where key = ?",
      { statement =>
        statement.setString(1, key)
      }
    )

  override def findAll(): Vector[GlobalDictionaryEntry] =
    readAll[GlobalDictionaryEntry]("select payload from global_dictionary order by key")

object PostgresGlobalDictionaryRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresGlobalDictionaryRepository =
    new PostgresGlobalDictionaryRepository(connectionFactory)
