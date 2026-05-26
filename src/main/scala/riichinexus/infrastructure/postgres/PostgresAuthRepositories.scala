package riichinexus.infrastructure.postgres

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.auth.objects.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable

final class PostgresAccountCredentialRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AccountCredentialRepository:
  override def save(credential: AccountCredential): AccountCredential =
    connectionFactory.withConnection(AccountCredentialTable.save(_, credential))

  override def findByUsername(username: String): Option[AccountCredential] =
    connectionFactory.withConnection(AccountCredentialTable.findByUsername(_, username))

  override def findByPlayerId(playerId: PlayerId): Option[AccountCredential] =
    connectionFactory.withConnection(AccountCredentialTable.findByPlayerId(_, playerId))

  override def findAll(): Vector[AccountCredential] =
    connectionFactory.withConnection(AccountCredentialTable.findAll)

object PostgresAccountCredentialRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAccountCredentialRepository =
    new PostgresAccountCredentialRepository(connectionFactory)

final class PostgresAuthenticatedSessionRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AuthenticatedSessionRepository:
  override def save(session: AuthenticatedSession): AuthenticatedSession =
    connectionFactory.withConnection(AuthenticatedSessionTable.save(_, session))

  override def findByToken(token: String): Option[AuthenticatedSession] =
    connectionFactory.withConnection(AuthenticatedSessionTable.findByToken(_, token))

  override def findAll(): Vector[AuthenticatedSession] =
    connectionFactory.withConnection(AuthenticatedSessionTable.findAll)

object PostgresAuthenticatedSessionRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAuthenticatedSessionRepository =
    new PostgresAuthenticatedSessionRepository(connectionFactory)
