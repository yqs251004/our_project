package riichinexus.microservices.dictionary.tables.globaldictionary

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.GlobalDictionaryEntry
import upickle.default.{read, write}

object GlobalDictionaryTable:
  private val upsertSql: String =
    """
      |insert into global_dictionary (key, updated_at, payload)
      |values (?, ?, cast(? as jsonb))
      |on conflict (key) do update set
      |  updated_at = excluded.updated_at,
      |  payload = excluded.payload
      |where cast(global_dictionary.payload ->> 'version' as integer) = ?
      |""".stripMargin

  private[riichinexus] def save(connection: Connection, entry: GlobalDictionaryEntry): GlobalDictionaryEntry =
    val persisted = entry.copy(version = entry.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.key)
      statement.setTimestamp(2, Timestamp.from(persisted.updatedAt))
      statement.setString(3, write[GlobalDictionaryEntry](persisted))
      statement.setInt(4, entry.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "global-dictionary-entry",
        aggregateId = persisted.key,
        expectedVersion = entry.version,
        actualVersion = findByKey(connection, persisted.key).map(_.version)
      )
    persisted

  private val findByKeySql: String =
    """
      |select payload
      |from global_dictionary
      |where key = ?
      |""".stripMargin

  private[riichinexus] def findByKey(connection: Connection, key: String): Option[GlobalDictionaryEntry] =
    Using.resource(connection.prepareStatement(findByKeySql)) { statement =>
      statement.setString(1, key)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readEntry(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from global_dictionary
      |order by key
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[GlobalDictionaryEntry] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readEntries)
    }

  private def readEntries(resultSet: ResultSet): Vector[GlobalDictionaryEntry] =
    @tailrec
    def loop(acc: Vector[GlobalDictionaryEntry]): Vector[GlobalDictionaryEntry] =
      if resultSet.next() then loop(readEntry(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readEntry(resultSet: ResultSet): GlobalDictionaryEntry =
    read[GlobalDictionaryEntry](resultSet.getString("payload"))
