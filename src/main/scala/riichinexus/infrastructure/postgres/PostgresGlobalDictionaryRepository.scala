package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.dictionary.objects.GlobalDictionaryEntry
import riichinexus.microservices.dictionary.tables.globaldictionary.GlobalDictionaryTable

final class PostgresGlobalDictionaryRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends GlobalDictionaryRepository:
  override def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry =
    connectionFactory.withConnection(GlobalDictionaryTable.save(_, entry))

  override def findByKey(key: String): Option[GlobalDictionaryEntry] =
    connectionFactory.withConnection(GlobalDictionaryTable.findByKey(_, key))

  override def findAll(): Vector[GlobalDictionaryEntry] =
    connectionFactory.withConnection(GlobalDictionaryTable.findAll)

object PostgresGlobalDictionaryRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresGlobalDictionaryRepository =
    new PostgresGlobalDictionaryRepository(connectionFactory)
