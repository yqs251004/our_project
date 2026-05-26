package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.dictionary.objects.DictionaryNamespaceRegistration
import riichinexus.microservices.dictionary.tables.dictionarynamespace.DictionaryNamespaceTable

final class PostgresDictionaryNamespaceRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DictionaryNamespaceRepository:
  override def save(registration: DictionaryNamespaceRegistration): DictionaryNamespaceRegistration =
    connectionFactory.withConnection(DictionaryNamespaceTable.save(_, registration))

  override def findByPrefix(prefix: String): Option[DictionaryNamespaceRegistration] =
    connectionFactory.withConnection(DictionaryNamespaceTable.findByPrefix(_, prefix))

  override def findAll(): Vector[DictionaryNamespaceRegistration] =
    connectionFactory.withConnection(DictionaryNamespaceTable.findAll)

object PostgresDictionaryNamespaceRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDictionaryNamespaceRepository =
    new PostgresDictionaryNamespaceRepository(connectionFactory)
