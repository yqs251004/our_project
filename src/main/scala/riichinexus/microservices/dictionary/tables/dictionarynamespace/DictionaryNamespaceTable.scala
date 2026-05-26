package riichinexus.microservices.dictionary.tables.dictionarynamespace

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.DictionaryNamespaceRegistration
import upickle.default.{read, write}

object DictionaryNamespaceTable:
  private val upsertSql: String =
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

  private[riichinexus] def save(
      connection: Connection,
      registration: DictionaryNamespaceRegistration
  ): DictionaryNamespaceRegistration =
    val persisted = registration.copy(version = registration.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.namespacePrefix)
      statement.setString(2, persisted.ownerPlayerId.value)
      statement.setString(3, persisted.status.toString)
      statement.setTimestamp(4, Timestamp.from(persisted.requestedAt))
      statement.setString(5, write[DictionaryNamespaceRegistration](persisted))
      statement.setInt(6, registration.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "dictionary-namespace",
        aggregateId = persisted.namespacePrefix,
        expectedVersion = registration.version,
        actualVersion = findByPrefix(connection, persisted.namespacePrefix).map(_.version)
      )
    persisted

  private val findByPrefixSql: String =
    """
      |select payload
      |from dictionary_namespaces
      |where namespace_prefix = ?
      |""".stripMargin

  private[riichinexus] def findByPrefix(
      connection: Connection,
      prefix: String
  ): Option[DictionaryNamespaceRegistration] =
    Using.resource(connection.prepareStatement(findByPrefixSql)) { statement =>
      statement.setString(1, prefix)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readRegistration(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from dictionary_namespaces
      |order by namespace_prefix
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[DictionaryNamespaceRegistration] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readRegistrations)
    }

  private def readRegistrations(resultSet: ResultSet): Vector[DictionaryNamespaceRegistration] =
    @tailrec
    def loop(acc: Vector[DictionaryNamespaceRegistration]): Vector[DictionaryNamespaceRegistration] =
      if resultSet.next() then loop(readRegistration(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readRegistration(resultSet: ResultSet): DictionaryNamespaceRegistration =
    read[DictionaryNamespaceRegistration](resultSet.getString("payload"))
